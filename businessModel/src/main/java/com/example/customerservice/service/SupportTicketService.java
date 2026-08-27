package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.SupportTicketStatus;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SupportTicket;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.exception.SupportTicketValidationException;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SupportTicketMapper;
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

@Service
@Slf4j
public class SupportTicketService {

    private final SupportTicketMapper ticketMapper;
    private final ChatSessionMapper sessionMapper;
    private final SysUserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public SupportTicketService(
            SupportTicketMapper ticketMapper,
            ChatSessionMapper sessionMapper,
            SysUserMapper userMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.ticketMapper = ticketMapper;
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
        next.setSessionId(ticket.getSessionId());
        next.setCreatedAt(ticket.getCreatedAt());
        next.setResolution(resolution);
        next.setResolvedAt(resolvedAt);
        SupportTicketVO view = toView(next, session);
        notifyAfterCommit(session, view, "TICKET_UPDATED");
        return view;
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
            notification.run();
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
