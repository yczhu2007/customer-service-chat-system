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
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SupportTicketMapper;
import com.example.customerservice.mapper.SysUserMapper;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SupportTicketService {

    private final SupportTicketMapper ticketMapper;
    private final ChatSessionMapper sessionMapper;
    private final SysUserMapper userMapper;

    public SupportTicketService(
            SupportTicketMapper ticketMapper,
            ChatSessionMapper sessionMapper,
            SysUserMapper userMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
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
        return toView(ticket, session);
    }

    @Transactional
    public SupportTicketVO updateTicket(String agentId, String ticketNo, SupportTicketUpdateDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("工单更新请求不能为空");
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
        if (!current.canTransitionTo(target)) {
            throw new IllegalArgumentException("不允许从 " + current + " 转换到 " + target);
        }
        String description = requireDescription(request.getDescription());
        String resolution = normalizeOptionalText(request.getResolution(), "处理结果");
        if (target == SupportTicketStatus.RESOLVED && resolution == null) {
            throw new IllegalArgumentException("处理结果不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        SupportTicket next = new SupportTicket();
        next.setId(ticket.getId());
        next.setStatus(target.name());
        next.setDescription(description);
        next.setResolution(resolution);
        next.setVersion(ticket.getVersion() + 1);
        next.setUpdatedAt(now);
        next.setResolvedAt(target == SupportTicketStatus.RESOLVED
                ? now
                : current == SupportTicketStatus.RESOLVED ? null : ticket.getResolvedAt());
        int updated = ticketMapper.update(
                next,
                Wrappers.<SupportTicket>lambdaUpdate()
                        .eq(SupportTicket::getId, ticket.getId())
                        .eq(SupportTicket::getVersion, request.getVersion())
        );
        if (updated != 1) {
            throw new BusinessStateException("工单已被其他操作修改，请刷新后重试");
        }
        next.setSessionId(ticket.getSessionId());
        next.setCreatedAt(ticket.getCreatedAt());
        return toView(next, session);
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
            throw new IllegalArgumentException("工单状态不合法");
        }
    }

    private long parseTicketNo(String ticketNo) {
        if (ticketNo == null || !ticketNo.matches("TK-\\d{8}")) {
            throw new IllegalArgumentException("工单编号格式不合法");
        }
        return Long.parseLong(ticketNo.substring(3));
    }

    private String requireDescription(String value) {
        String description = normalizeOptionalText(value, "问题描述");
        if (description == null) {
            throw new IllegalArgumentException("问题描述不能为空");
        }
        return description;
    }

    private String normalizeOptionalText(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException(label + "长度不能超过1000个字符");
        }
        return normalized;
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
}
