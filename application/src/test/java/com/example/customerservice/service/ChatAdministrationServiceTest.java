package com.example.customerservice.service;

import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.impl.ChatAdministrationService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAdministrationServiceTest {

    @Test
    void createsSessionForEnabledUserAndAgentAccounts() {
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper roleMapper = mock(SysUserRoleMapper.class);
        ChatSessionOperations sessionOperations = mock(ChatSessionOperations.class);
        ChatSessionDeletionService deletionService = mock(ChatSessionDeletionService.class);
        SysUser user = user("U001", "user-login");
        SysUser agent = user("A001", "agent-login");
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        session.setStatus("ACTIVE");
        when(userMapper.findByUsername("user-login")).thenReturn(user);
        when(userMapper.findByUsername("agent-login")).thenReturn(agent);
        when(roleMapper.findRoleCodesByUserId("U001")).thenReturn(Set.of("USER"));
        when(roleMapper.findRoleCodesByUserId("A001")).thenReturn(Set.of("AGENT"));
        when(sessionOperations.createSession("U001", "A001")).thenReturn(session);
        ChatAdministrationService service = new ChatAdministrationService(
                sessionMapper, messageMapper, userMapper, roleMapper,
                sessionOperations, deletionService
        );

        SessionSummaryVO result = service.createSession("user-login", "agent-login");

        assertEquals("S001", result.sessionId());
        verify(sessionOperations).notifyBothParties(session);
    }

    @Test
    void rejectsCreatingAnotherSessionForUserWithActiveSession() {
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper roleMapper = mock(SysUserRoleMapper.class);
        ChatSessionOperations sessionOperations = mock(ChatSessionOperations.class);
        SysUser user = user("U001", "user-login");
        SysUser agent = user("A001", "agent-login");
        when(userMapper.findByUsername("user-login")).thenReturn(user);
        when(userMapper.findByUsername("agent-login")).thenReturn(agent);
        when(roleMapper.findRoleCodesByUserId("U001")).thenReturn(Set.of("USER"));
        when(roleMapper.findRoleCodesByUserId("A001")).thenReturn(Set.of("AGENT"));
        when(sessionMapper.findActiveByUserId("U001")).thenReturn(new ChatSession());
        ChatAdministrationService service = new ChatAdministrationService(
                sessionMapper, mock(ChatMessageMapper.class), userMapper, roleMapper,
                sessionOperations, mock(ChatSessionDeletionService.class)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createSession("user-login", "agent-login")
        );

        assertEquals("该用户已有进行中的会话", exception.getMessage());
        verify(sessionOperations, never()).createSession("U001", "A001");
    }

    @Test
    void endsActiveSessionBeforePermanentlyDeletingIt() {
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatSessionOperations sessionOperations = mock(ChatSessionOperations.class);
        ChatSessionDeletionService deletionService = mock(ChatSessionDeletionService.class);
        ChatSession session = new ChatSession();
        session.setStatus("ACTIVE");
        session.setAgentId("A001");
        when(sessionMapper.selectById("S001")).thenReturn(session);
        ChatAdministrationService service = new ChatAdministrationService(
                sessionMapper, mock(ChatMessageMapper.class), mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class), sessionOperations, deletionService
        );

        service.deleteSession("S001");

        org.mockito.InOrder order = inOrder(sessionOperations, deletionService);
        order.verify(sessionOperations).endSessionByAgent("S001", "A001");
        order.verify(deletionService).deleteSession("S001");
    }

    @Test
    void rejectsDeletingMessageThatDoesNotExist() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        when(messageMapper.deleteById("M001")).thenReturn(0);
        ChatAdministrationService service = new ChatAdministrationService(
                mock(ChatSessionMapper.class), messageMapper, mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class), mock(ChatSessionOperations.class),
                mock(ChatSessionDeletionService.class)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteMessage("M001")
        );

        assertEquals("消息不存在", exception.getMessage());
    }

    private SysUser user(String id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus("ENABLED");
        return user;
    }
}
