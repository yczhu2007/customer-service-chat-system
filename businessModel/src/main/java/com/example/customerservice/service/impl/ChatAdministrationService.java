package com.example.customerservice.service.impl;

import com.example.customerservice.constant.AccountStatus;

import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RoleCodes;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.ChatAdministrationOperations;
import com.example.customerservice.service.ChatSessionDeletionService;
import com.example.customerservice.service.ChatSessionOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

/** 管理员手工创建、删除会话及删除消息的业务编排。 */
@Service
public class ChatAdministrationService implements ChatAdministrationOperations {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ChatSessionOperations sessionOperations;
    private final ChatSessionDeletionService sessionDeletionService;

    public ChatAdministrationService(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            SysUserMapper userMapper,
            SysUserRoleMapper userRoleMapper,
            ChatSessionOperations sessionOperations,
            ChatSessionDeletionService sessionDeletionService
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.sessionOperations = sessionOperations;
        this.sessionDeletionService = sessionDeletionService;
    }

    @Override
    public SessionSummaryVO createSession(String userLoginNumber, String agentLoginNumber) {
        SysUser user = requireEnabledUser(userLoginNumber, "用户");
        SysUser agent = requireEnabledUser(agentLoginNumber, "客服");
        requireRole(user, RoleCodes.USER, "用户登录编号对应的账号没有USER角色");
        requireRole(agent, RoleCodes.AGENT, "客服登录编号对应的账号没有AGENT角色");
        if (sessionMapper.findActiveByUserId(user.getId()) != null) {
            throw new IllegalArgumentException("该用户已有进行中的会话");
        }
        ChatSession session = sessionOperations.createSession(user.getId(), agent.getId());
        sessionOperations.notifyBothParties(session);
        return new SessionSummaryVO(
                session.getId(), user.getId(), user.getUsername(), agent.getId(), agent.getUsername(),
                session.getStatus(), session.getTitle(), session.getCreateTime(), session.getEndTime(),
                null, null, 0L, null, null, null
        );
    }

    @Override
    public void deleteMessage(String messageId) {
        if (messageId == null || messageId.isBlank() || messageId.length() > 64) {
            throw new IllegalArgumentException("消息ID无效");
        }
        if (messageMapper.deleteById(messageId) == 0) {
            throw new IllegalArgumentException("消息不存在");
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus()) && session.getAgentId() != null) {
            sessionOperations.endSessionByAgent(sessionId, session.getAgentId());
        }
        sessionDeletionService.deleteSession(sessionId);
    }

    private SysUser requireEnabledUser(String loginNumber, String accountType) {
        SysUser user = userMapper.findByUsername(loginNumber == null ? null : loginNumber.trim());
        if (user == null || !AccountStatus.ENABLED.equals(user.getStatus())) {
            throw new IllegalArgumentException(accountType + "登录编号不存在或账号已禁用");
        }
        return user;
    }

    private void requireRole(SysUser user, String roleCode, String message) {
        Set<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        if (roles == null || !roles.contains(roleCode)) {
            throw new IllegalArgumentException(message);
        }
    }
}
