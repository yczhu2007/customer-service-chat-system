package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SupportTicket;
import com.example.customerservice.domain.SupportTicketStatusHistory;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.exception.SupportTicketValidationException;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SupportTicketMapper;
import com.example.customerservice.mapper.SupportTicketStatusHistoryMapper;
import com.example.customerservice.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.shiro.authz.UnauthorizedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock private SupportTicketMapper ticketMapper;
    @Mock private SupportTicketStatusHistoryMapper historyMapper;
    @Mock private ChatSessionMapper sessionMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private SupportTicketService service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "SupportTicketServiceTest"),
                SupportTicket.class
        );
    }

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(ticketMapper, historyMapper, sessionMapper, userMapper, messagingTemplate);
    }

    @Test
    void assignedAgentCanCreateOneTicketForAnActiveSession() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.insert(any(SupportTicket.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SupportTicket.class).setId(125L);
            return 1;
        });
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));

        SupportTicketVO ticket = service.createTicket("A001", "S001", createRequest("无法完成订单支付"));

        assertEquals("TK-00000125", ticket.getTicketNo());
        assertEquals("OPEN", ticket.getStatus());
        assertEquals("无法完成订单支付", ticket.getDescription());
        verify(ticketMapper).insert(any(SupportTicket.class));
        verify(historyMapper).insert(org.mockito.ArgumentMatchers.<SupportTicketStatusHistory>argThat(history ->
                history.getFromStatus() == null && "OPEN".equals(history.getToStatus())
                        && "A001".equals(history.getOperatorId())
        ));
    }

    @Test
    void nonAssignedAgentCannotCreateTicket() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));

        assertThrows(UnauthorizedException.class,
                () -> service.createTicket("A999", "S001", createRequest("支付失败")));

        verify(ticketMapper, never()).insert(any(SupportTicket.class));
    }

    @Test
    void sessionUserCanReadNoTicketButUnrelatedUserCannotRead() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.selectOne(any())).thenReturn(null);

        assertNull(service.findBySessionId("U001", false, "S001"));
        assertThrows(UnauthorizedException.class,
                () -> service.findBySessionId("U999", false, "S001"));
    }

    @Test
    void resolvingTicketRequiresResolution() {
        when(ticketMapper.selectById(125L)).thenReturn(ticket("S001", "IN_PROGRESS", 0));
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateTicket("A001", "TK-00000125", updateRequest("RESOLVED", "支付失败", null, 0)));
    }

    @Test
    void versionConflictDoesNotOverwriteTicket() {
        when(ticketMapper.selectById(125L)).thenReturn(ticket("S001", "OPEN", 0));
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.update(any(SupportTicket.class), any())).thenReturn(0);

        BusinessStateException exception = assertThrows(BusinessStateException.class,
                () -> service.updateTicket("A001", "TK-00000125", updateRequest("IN_PROGRESS", "支付失败", null, 0)));

        assertEquals("工单已被其他操作修改，请刷新后重试", exception.getMessage());
    }

    @Test
    void closedSessionCanCreateTicketAndDuplicateCreateIsAConflict() {
        ChatSession closedSession = session("S001", "U001", "A001");
        closedSession.setStatus("CLOSED");
        when(sessionMapper.selectById("S001")).thenReturn(closedSession);
        when(ticketMapper.insert(any(SupportTicket.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SupportTicket.class).setId(126L);
            return 1;
        });
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));

        assertEquals("TK-00000126", service.createTicket("A001", "S001", createRequest("关闭后继续跟进")).getTicketNo());

        when(ticketMapper.insert(any(SupportTicket.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(BusinessStateException.class,
                () -> service.createTicket("A001", "S001", createRequest("重复创建")));
    }

    @Test
    void assignedAgentAndAdministratorCanReadButUnrelatedAgentCannot() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.selectOne(any())).thenReturn(ticket("S001", "OPEN", 0));
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));

        assertEquals("TK-00000125", service.findBySessionId("A001", false, "S001").getTicketNo());
        assertEquals("TK-00000125", service.findBySessionId("ADMIN001", true, "S001").getTicketNo());
        assertThrows(UnauthorizedException.class,
                () -> service.findBySessionId("A999", false, "S001"));
    }

    @Test
    void resolvingThenReopeningSetsAndClearsResolvedTime() {
        SupportTicket inProgress = ticket("S001", "IN_PROGRESS", 0);
        when(ticketMapper.selectById(125L)).thenReturn(inProgress);
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));
        when(ticketMapper.update(any(SupportTicket.class), any())).thenReturn(1);

        SupportTicketVO resolved = service.updateTicket("A001", "TK-00000125", updateRequest("RESOLVED", "支付失败", "已修复", 0));
        assertEquals("RESOLVED", resolved.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(resolved.getResolvedAt());

        SupportTicket resolvedTicket = ticket("S001", "RESOLVED", 1);
        resolvedTicket.setResolvedAt(LocalDateTime.of(2026, 8, 26, 11, 0));
        when(ticketMapper.selectById(125L)).thenReturn(resolvedTicket);
        SupportTicketVO reopened = service.updateTicket("A001", "TK-00000125", updateRequest("IN_PROGRESS", "继续跟进", null, 1));

        assertEquals("IN_PROGRESS", reopened.getStatus());
        assertNull(reopened.getResolvedAt());
        verify(ticketMapper).update(org.mockito.ArgumentMatchers.<SupportTicket>argThat(
                saved -> "IN_PROGRESS".equals(saved.getStatus()) && saved.getResolvedAt() == null
        ), any());
    }

    @Test
    void assignedAgentCanEditTicketWithoutChangingStatus() {
        when(ticketMapper.selectById(125L)).thenReturn(ticket("S001", "IN_PROGRESS", 0));
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));
        when(ticketMapper.update(any(SupportTicket.class), any())).thenReturn(1);

        SupportTicketVO updated = service.updateTicket(
                "A001",
                "TK-00000125",
                updateRequest("IN_PROGRESS", "补充后的问题描述", null, 0)
        );

        assertEquals("IN_PROGRESS", updated.getStatus());
        assertEquals("补充后的问题描述", updated.getDescription());
        verify(historyMapper, never()).insert(any(SupportTicketStatusHistory.class));
    }

    @Test
    void statusChangeWritesOneHistoryRecordAndSessionParticipantCanReadIt() {
        when(ticketMapper.selectById(125L)).thenReturn(ticket("S001", "OPEN", 0));
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));
        when(ticketMapper.update(any(SupportTicket.class), any())).thenReturn(1);

        service.updateTicket("A001", "TK-00000125", updateRequest("IN_PROGRESS", "支付失败", null, 0));

        verify(historyMapper).insert(org.mockito.ArgumentMatchers.<SupportTicketStatusHistory>argThat(history ->
                "OPEN".equals(history.getFromStatus()) && "IN_PROGRESS".equals(history.getToStatus())
        ));
        assertThrows(UnauthorizedException.class, () -> service.findHistoryBySessionId("U999", false, "S001"));
    }

    @Test
    void reopeningTicketWritesNullResolutionAndResolvedTimeToSql() {
        SupportTicket resolvedTicket = ticket("S001", "RESOLVED", 1);
        resolvedTicket.setResolution("旧处理结果");
        resolvedTicket.setResolvedAt(LocalDateTime.of(2026, 8, 26, 11, 0));
        when(ticketMapper.selectById(125L)).thenReturn(resolvedTicket);
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));
        when(ticketMapper.update(any(SupportTicket.class), any())).thenReturn(1);

        service.updateTicket(
                "A001",
                "TK-00000125",
                updateRequest("IN_PROGRESS", "继续跟进", null, 1)
        );

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ticketMapper).update(any(SupportTicket.class), wrapperCaptor.capture());
        @SuppressWarnings("unchecked")
        LambdaUpdateWrapper<SupportTicket> updateWrapper =
                (LambdaUpdateWrapper<SupportTicket>) wrapperCaptor.getValue();
        assertTrue(updateWrapper.getSqlSet().contains("resolution"));
        assertTrue(updateWrapper.getSqlSet().contains("resolved_at"));
    }

    @Test
    void illegalTransitionReturnsAReadableTicketValidationMessage() {
        when(ticketMapper.selectById(125L)).thenReturn(ticket("S001", "IN_PROGRESS", 0));
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));

        SupportTicketValidationException exception = assertThrows(
                SupportTicketValidationException.class,
                () -> service.updateTicket(
                        "A001",
                        "TK-00000125",
                        updateRequest("OPEN", "支付失败", null, 0)
                )
        );

        assertEquals("不允许从处理中转换到待处理", exception.getMessage());
    }

    @Test
    void ticketNotificationIsSentOnlyAfterTransactionCommit() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.insert(any(SupportTicket.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SupportTicket.class).setId(125L);
            return 1;
        });
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createTicket("A001", "S001", createRequest("无法完成订单支付"));

            verifyNoInteractions(messagingTemplate);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(messagingTemplate).convertAndSendToUser(
                    eq("U001"), eq("/queue/chat"), org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(body ->
                            "TICKET_CREATED".equals(body.get("event"))
                                    && "S001".equals(body.get("sessionId"))
                                    && "TK-00000125".equals(body.get("ticketNo"))
                                    && Integer.valueOf(0).equals(body.get("version"))
                    )
            );
            verify(messagingTemplate).convertAndSendToUser(
                    eq("A001"), eq("/queue/chat"), org.mockito.ArgumentMatchers.anyMap()
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void notificationFailureDoesNotTurnCommittedTicketIntoAnError() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.insert(any(SupportTicket.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SupportTicket.class).setId(125L);
            return 1;
        });
        when(userMapper.selectById("A001")).thenReturn(user("A001", "客服一"));
        doThrow(new RuntimeException("broker unavailable"))
                .when(messagingTemplate).convertAndSendToUser(eq("U001"), eq("/queue/chat"), any());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createTicket("A001", "S001", createRequest("无法完成订单支付"));

            assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void directInvocationWithoutTransactionDoesNotNotifyBeforeCommit() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));
        when(ticketMapper.insert(any(SupportTicket.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SupportTicket.class).setId(125L);
            return 1;
        });

        service.createTicket("A001", "S001", createRequest("直接调用工单"));

        verifyNoInteractions(messagingTemplate);
    }

    private static ChatSession session(String id, String userId, String agentId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setAgentId(agentId);
        session.setTitle("订单咨询");
        session.setPriority("HIGH");
        session.setCategory("PAYMENT");
        return session;
    }

    private static SysUser user(String id, String nickname) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

    private static SupportTicket ticket(String sessionId, String status, int version) {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(125L);
        ticket.setSessionId(sessionId);
        ticket.setStatus(status);
        ticket.setDescription("支付失败");
        ticket.setVersion(version);
        ticket.setCreatedAt(LocalDateTime.of(2026, 8, 26, 10, 0));
        ticket.setUpdatedAt(LocalDateTime.of(2026, 8, 26, 10, 0));
        return ticket;
    }

    private static SupportTicketCreateDTO createRequest(String description) {
        SupportTicketCreateDTO request = new SupportTicketCreateDTO();
        request.setDescription(description);
        return request;
    }

    private static SupportTicketUpdateDTO updateRequest(String status, String description, String resolution, int version) {
        SupportTicketUpdateDTO request = new SupportTicketUpdateDTO();
        request.setStatus(status);
        request.setDescription(description);
        request.setResolution(resolution);
        request.setVersion(version);
        return request;
    }
}
