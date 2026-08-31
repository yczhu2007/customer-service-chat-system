package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.SupportTicketStatus;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SupportTicket;
import com.example.customerservice.domain.SupportTicketStatusHistory;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.dto.SupportTicketStatusHistoryVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.exception.SupportTicketValidationException;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SupportTicketMapper;
import com.example.customerservice.mapper.SupportTicketStatusHistoryMapper;
import com.example.customerservice.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class SupportTicketService {

    private final SupportTicketMapper ticketMapper;
    private final SupportTicketStatusHistoryMapper historyMapper;
    private final ChatSessionMapper sessionMapper;
    private final SysUserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public SupportTicketService(
            SupportTicketMapper ticketMapper,
            SupportTicketStatusHistoryMapper historyMapper,
            ChatSessionMapper sessionMapper,
            SysUserMapper userMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.ticketMapper = ticketMapper;
        this.historyMapper = historyMapper;
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
        this.messagingTemplate = messagingTemplate;
    }

    public SupportTicketVO findBySessionId(String callerId, boolean administrator, String sessionId) {
        ChatSession session = requireSession(sessionId);
        requireParticipant(session, callerId, administrator);
        SupportTicket ticket = ticketMapper.selectOne(
                Wrappers.<SupportTicket>lambdaQuery()
                        .eq(SupportTicket::getSessionId, sessionId)
        );
        return ticket == null ? null : toView(ticket, session);
    }

    @Transactional
    public SupportTicketVO createTicket(String agentId, String sessionId, SupportTicketCreateDTO request) {
        ChatSession session = requireSession(sessionId);
        requireAssignedAgent(session, agentId);
        String description = requireDescription(request == null ? null : request.getDescription());
        LocalDateTime now = LocalDateTime.now();
        SupportTicket ticket = new SupportTicket();
        ticket.setSessionId(sessionId);
        ticket.setStatus(SupportTicketStatus.OPEN.name());
        ticket.setDescription(description);
        ticket.setVersion(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        try {
            if (ticketMapper.insert(ticket) != 1 || ticket.getId() == null) {
                throw new IllegalStateException("工单创建失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessStateException("该会话已创建工单", exception);
        }
        writeStatusHistory(ticket.getId(), agentId, "CREATED", null, SupportTicketStatus.OPEN.name(), now);
        SupportTicketVO view = toView(ticket, session);
        notifyAfterCommit(session, view, "TICKET_CREATED");
        return view;
    }

    @Transactional
    public SupportTicketVO updateTicket(String agentId, String ticketNo, SupportTicketUpdateDTO request) {
        if (request == null) {
            throw new SupportTicketValidationException("工单更新请求不能为空");
        }
        SupportTicket ticket = ticketMapper.selectById(parseTicketNo(ticketNo));
        if (ticket == null) {
            throw new NotFoundException("工单不存在");
        }
        ChatSession session = requireSession(ticket.getSessionId());
        requireAssignedAgent(session, agentId);
        if (!ticket.getVersion().equals(request.getVersion())) {
            throw new BusinessStateException("工单已被其他操作修改，请刷新后重试");
        }
        SupportTicketStatus current = parseStatus(ticket.getStatus());
        SupportTicketStatus target = parseStatus(request.getStatus());
        if (current != target && !current.canTransitionTo(target)) {
            throw new SupportTicketValidationException(
                    "不允许从" + statusLabel(current) + "转换到" + statusLabel(target)
            );
        }
        String description = requireDescription(request.getDescription());
        String resolution = normalizeOptionalText(request.getResolution(), "处理结果");
        if (target == SupportTicketStatus.RESOLVED && resolution == null) {
            throw new SupportTicketValidationException("处理结果不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        SupportTicket next = new SupportTicket();
        next.setId(ticket.getId());
        next.setStatus(target.name());
        next.setDescription(description);
        next.setVersion(ticket.getVersion() + 1);
        next.setUpdatedAt(now);
        LocalDateTime resolvedAt = target == SupportTicketStatus.RESOLVED
                ? (current == SupportTicketStatus.RESOLVED ? ticket.getResolvedAt() : now)
                : null;
        int updated = ticketMapper.update(
                next,
                Wrappers.<SupportTicket>lambdaUpdate()
                        .set(SupportTicket::getResolution, resolution)
                        .set(SupportTicket::getResolvedAt, resolvedAt)
                        .eq(SupportTicket::getId, ticket.getId())
                        .eq(SupportTicket::getVersion, request.getVersion())
        );
        if (updated != 1) {
            throw new BusinessStateException("工单已被其他操作修改，请刷新后重试");
        }
        boolean contentChanged = !Objects.equals(ticket.getDescription(), description)
                || !Objects.equals(ticket.getResolution(), resolution);
        if (current != target) {
            writeStatusHistory(ticket.getId(), agentId, "STATUS_CHANGED", current.name(), target.name(), now);
        } else if (contentChanged) {
            writeStatusHistory(ticket.getId(), agentId, "CONTENT_UPDATED", current.name(), target.name(), now);
        }
        next.setSessionId(ticket.getSessionId());
        next.setCreatedAt(ticket.getCreatedAt());
        next.setResolution(resolution);
        next.setResolvedAt(resolvedAt);
        SupportTicketVO view = toView(next, session);
        notifyAfterCommit(session, view, "TICKET_UPDATED");
        return view;
    }

    @Transactional
    public SupportTicketVO confirmResolution(String userId, String ticketNo, int version) {
        SupportTicket ticket = requireResolvedTicketForUser(userId, ticketNo, version);
        LocalDateTime now = LocalDateTime.now();
        SupportTicket next = copyTicketForUpdate(ticket, ticket.getStatus(), ticket.getResolution(), ticket.getResolvedAt(), now, now);
        updateByVersion(next, version);
        writeStatusHistory(ticket.getId(), userId, "USER_CONFIRMED", SupportTicketStatus.RESOLVED.name(), SupportTicketStatus.RESOLVED.name(), now);
        ChatSession session = requireSession(ticket.getSessionId());
        SupportTicketVO view = toView(next, session);
        notifyAfterCommit(session, view, "TICKET_UPDATED");
        return view;
    }

    @Transactional
    public SupportTicketVO requestFurtherHandling(String userId, String ticketNo, int version) {
        SupportTicket ticket = requireResolvedTicketForUser(userId, ticketNo, version);
        LocalDateTime now = LocalDateTime.now();
        SupportTicket next = copyTicketForUpdate(ticket, SupportTicketStatus.IN_PROGRESS.name(), ticket.getResolution(), null, null, now);
        updateByVersion(next, version);
        writeStatusHistory(ticket.getId(), userId, "REOPENED", SupportTicketStatus.RESOLVED.name(), SupportTicketStatus.IN_PROGRESS.name(), now);
        ChatSession session = requireSession(ticket.getSessionId());
        SupportTicketVO view = toView(next, session);
        notifyAfterCommit(session, view, "TICKET_UPDATED");
        return view;
    }

    public List<SupportTicketStatusHistoryVO> findHistoryBySessionId(
            String callerId,
            boolean administrator,
            String sessionId
    ) {
        ChatSession session = requireSession(sessionId);
        requireParticipant(session, callerId, administrator);
        SupportTicket ticket = ticketMapper.selectOne(
                Wrappers.<SupportTicket>lambdaQuery()
                        .eq(SupportTicket::getSessionId, sessionId)
        );
        if (ticket == null) {
            return List.of();
        }
        List<SupportTicketStatusHistoryVO> history = historyMapper.findRecentByTicketId(ticket.getId());
        return history == null ? List.of() : List.copyOf(history);
    }

    public PageResult<SupportTicketStatusHistoryVO> findHistoryPageBySessionId(
            String callerId, boolean administrator, String sessionId, long pageNo, long pageSize
    ) {
        ChatSession session = requireSession(sessionId);
        requireParticipant(session, callerId, administrator);
        SupportTicket ticket = ticketMapper.selectOne(Wrappers.<SupportTicket>lambdaQuery()
                .eq(SupportTicket::getSessionId, sessionId));
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(50L, pageSize));
        if (ticket == null) return new PageResult<>(normalizedPageNo, normalizedPageSize, 0, 0, List.of());
        long total = historyMapper.countByTicketId(ticket.getId());
        long pages = total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize;
        List<SupportTicketStatusHistoryVO> records = total == 0 ? List.of()
                : historyMapper.findByTicketId(ticket.getId(), (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);
        return new PageResult<>(normalizedPageNo, normalizedPageSize, total, pages,
                records == null ? List.of() : List.copyOf(records));
    }

    private ChatSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        return session;
    }

    private void requireParticipant(ChatSession session, String callerId, boolean administrator) {
        if (administrator || callerId.equals(session.getUserId()) || callerId.equals(session.getAgentId())) {
            return;
        }
        throw new UnauthorizedException("无权查看该会话工单");
    }

    private void requireAssignedAgent(ChatSession session, String agentId) {
        if (agentId != null && agentId.equals(session.getAgentId())) {
            return;
        }
        throw new UnauthorizedException("只有当前负责客服可以操作工单");
    }

    private SupportTicket requireResolvedTicketForUser(String userId, String ticketNo, int version) {
        SupportTicket ticket = ticketMapper.selectById(parseTicketNo(ticketNo));
        if (ticket == null) {
            throw new NotFoundException("工单不存在");
        }
        ChatSession session = requireSession(ticket.getSessionId());
        if (!session.getUserId().equals(userId)) {
            throw new UnauthorizedException("只有会话用户可以确认工单结果");
        }
        if (!ticket.getVersion().equals(version)) {
            throw new BusinessStateException("工单已被其他操作修改，请刷新后重试");
        }
        if (parseStatus(ticket.getStatus()) != SupportTicketStatus.RESOLVED) {
            throw new SupportTicketValidationException("只有已解决工单可以进行用户确认");
        }
        return ticket;
    }

    private SupportTicket copyTicketForUpdate(
            SupportTicket ticket,
            String status,
            String resolution,
            LocalDateTime resolvedAt,
            LocalDateTime userConfirmedAt,
            LocalDateTime updatedAt
    ) {
        SupportTicket next = new SupportTicket();
        next.setId(ticket.getId());
        next.setSessionId(ticket.getSessionId());
        next.setStatus(status);
        next.setDescription(ticket.getDescription());
        next.setResolution(resolution);
        next.setVersion(ticket.getVersion() + 1);
        next.setCreatedAt(ticket.getCreatedAt());
        next.setUpdatedAt(updatedAt);
        next.setResolvedAt(resolvedAt);
        next.setUserConfirmedAt(userConfirmedAt);
        return next;
    }

    private void updateByVersion(SupportTicket ticket, int version) {
        int updated = ticketMapper.update(
                ticket,
                Wrappers.<SupportTicket>lambdaUpdate()
                        .set(SupportTicket::getResolution, ticket.getResolution())
                        .set(SupportTicket::getResolvedAt, ticket.getResolvedAt())
                        .set(SupportTicket::getUserConfirmedAt, ticket.getUserConfirmedAt())
                        .eq(SupportTicket::getId, ticket.getId())
                        .eq(SupportTicket::getVersion, version)
        );
        if (updated != 1) {
            throw new BusinessStateException("工单已被其他操作修改，请刷新后重试");
        }
    }

    private SupportTicketStatus parseStatus(String value) {
        try {
            return SupportTicketStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SupportTicketValidationException("工单状态不合法");
        }
    }

    private long parseTicketNo(String ticketNo) {
        if (ticketNo == null || !ticketNo.matches("TK-\\d{8}")) {
            throw new SupportTicketValidationException("工单编号格式不合法");
        }
        return Long.parseLong(ticketNo.substring(3));
    }

    private String requireDescription(String value) {
        String description = normalizeOptionalText(value, "问题描述");
        if (description == null) {
            throw new SupportTicketValidationException("问题描述不能为空");
        }
        return description;
    }

    private String normalizeOptionalText(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new SupportTicketValidationException(label + "长度不能超过1000个字符");
        }
        return normalized;
    }

    private String statusLabel(SupportTicketStatus status) {
        return switch (status) {
            case OPEN -> "待处理";
            case IN_PROGRESS -> "处理中";
            case WAITING_USER -> "等待用户";
            case RESOLVED -> "已解决";
        };
    }

    private void writeStatusHistory(
            Long ticketId,
            String operatorId,
            String actionType,
            String fromStatus,
            String toStatus,
            LocalDateTime createdAt
    ) {
        SupportTicketStatusHistory history = new SupportTicketStatusHistory();
        history.setTicketId(ticketId);
        history.setOperatorId(operatorId);
        history.setActionType(actionType);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setCreatedAt(createdAt);
        historyMapper.insert(history);
    }

    private SupportTicketVO toView(SupportTicket ticket, ChatSession session) {
        SupportTicketVO view = new SupportTicketVO();
        view.setTicketNo("TK-%08d".formatted(ticket.getId()));
        view.setSessionId(ticket.getSessionId());
        view.setStatus(ticket.getStatus());
        view.setDescription(ticket.getDescription());
        view.setResolution(ticket.getResolution());
        view.setVersion(ticket.getVersion());
        view.setCreatedAt(ticket.getCreatedAt());
        view.setUpdatedAt(ticket.getUpdatedAt());
        view.setResolvedAt(ticket.getResolvedAt());
        view.setUserConfirmedAt(ticket.getUserConfirmedAt());
        view.setTitle(session.getTitle());
        view.setPriority(session.getPriority());
        view.setCategory(session.getCategory());
        if (session.getAgentId() != null) {
            SysUser agent = userMapper.selectById(session.getAgentId());
            if (agent != null) {
                view.setAgentNickname(agent.getNickname() == null || agent.getNickname().isBlank()
                        ? agent.getUsername() : agent.getNickname());
            }
        }
        return view;
    }

    private void notifyAfterCommit(ChatSession session, SupportTicketVO ticket, String event) {
        Runnable notification = () -> {
            Map<String, Object> payload = Map.of(
                    "event", event,
                    "sessionId", session.getId(),
                    "ticketNo", ticket.getTicketNo(),
                    "version", ticket.getVersion()
            );
            notifyParticipant(session.getUserId(), payload, ticket.getTicketNo(), session.getId());
            notifyParticipant(session.getAgentId(), payload, ticket.getTicketNo(), session.getId());
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("工单操作未处于事务同步上下文，跳过实时通知：工单{}，会话{}",
                    ticket.getTicketNo(), session.getId());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notification.run();
            }
        });
    }

    private void notifyParticipant(String userId, Map<String, Object> payload, String ticketNo, String sessionId) {
        try {
            messagingTemplate.convertAndSendToUser(userId, "/queue/chat", payload);
        } catch (RuntimeException exception) {
            log.warn("工单实时通知发送失败，工单：{}，会话：{}", ticketNo, sessionId);
        }
    }
}
