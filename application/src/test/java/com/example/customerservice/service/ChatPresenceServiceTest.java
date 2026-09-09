package com.example.customerservice.service;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.ChatSessionDTO;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class ChatPresenceServiceTest {

    @Mock private ChatRedisRepository chatRedisRepository;
    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChatMessageReadMapper chatMessageReadMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private ObjectProvider<ChatPresenceCallbacks> callbacksProvider;
    @Mock private ChatPresenceCallbacks callbacks;

    private ChatPresenceService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(callbacksProvider.getObject()).thenReturn(callbacks);
        service = new ChatPresenceService(
                chatRedisRepository,
                chatSessionMapper,
                chatMessageReadMapper,
                messagingTemplate,
                sysUserRoleMapper,
                callbacksProvider,
                30,
                5
        );
    }

    @Test
    void staleDisconnectKeepsNewConnectionAndStopsRenewal() {
        @SuppressWarnings("unchecked")
        java.util.concurrent.ScheduledFuture<?> renewal =
                (java.util.concurrent.ScheduledFuture<?>) org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        when(chatRedisRepository.getValue(RedisConstants.WS_SESSION + "WS-old")).thenReturn("U001");
        when(chatRedisRepository.acquireLock(
                RedisConstants.PRESENCE_OPERATION_LOCK + "U001",
                RedisConstants.PRESENCE_OPERATION_LOCK_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        )).thenReturn("lock-token");
        when(chatRedisRepository.startLockRenewal(
                RedisConstants.PRESENCE_OPERATION_LOCK + "U001",
                "lock-token",
                RedisConstants.PRESENCE_OPERATION_LOCK_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        )).thenReturn((java.util.concurrent.ScheduledFuture) renewal);
        when(chatRedisRepository.getValue(RedisConstants.USER_WS + "U001")).thenReturn("WS-new");

        service.handleDisconnect("WS-old");

        verify(chatRedisRepository).delete(RedisConstants.WS_SESSION + "WS-old");
        verify(chatRedisRepository).stopLockRenewal(renewal);
        verify(chatRedisRepository).releaseLock(
                RedisConstants.PRESENCE_OPERATION_LOCK + "U001", "lock-token"
        );
        verify(chatSessionMapper, org.mockito.Mockito.never()).selectById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void agentOfflineImmediatelyNotifiesBothPartiesThatSessionClosed() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        session.setStatus(ChatConstants.SESSION_STATUS_ACTIVE);
        session.setCreateTime(LocalDateTime.now());

        when(chatRedisRepository.setMembers(RedisConstants.agentSessionsKey("A001")))
                .thenReturn(Set.of("S001"));
        when(chatSessionMapper.selectById("S001")).thenReturn(session);
        when(callbacks.finalizePresenceSession(session, "A001")).thenReturn(true);
        when(chatRedisRepository.hasKey(RedisConstants.USER_WS + "U001")).thenReturn(false);

        service.disconnectAgent("A001", ChatConstants.REASON_AGENT_OFFLINE);

        ArgumentCaptor<ChatSessionDTO> userNotice = ArgumentCaptor.forClass(ChatSessionDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("U001"), eq("/queue/chat"), userNotice.capture()
        );
        assertEquals(ChatConstants.EVENT_SESSION_CLOSED, userNotice.getValue().getEvent());
        assertEquals(ChatConstants.REASON_AGENT_OFFLINE, userNotice.getValue().getReason());
        assertEquals(ChatConstants.SESSION_STATUS_CLOSED, userNotice.getValue().getStatus());

        ArgumentCaptor<ChatSessionDTO> agentNotice = ArgumentCaptor.forClass(ChatSessionDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("A001"), eq("/queue/chat"), agentNotice.capture()
        );
        assertEquals(ChatConstants.EVENT_SESSION_CLOSED, agentNotice.getValue().getEvent());
        assertEquals("S001", agentNotice.getValue().getSessionId());
    }

    @Test
    void agentHeartbeatTimeoutNotifiesActiveUsersDuringReconnectGrace() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        session.setStatus(ChatConstants.SESSION_STATUS_ACTIVE);

        @SuppressWarnings("unchecked")
        java.util.concurrent.ScheduledFuture<?> renewal =
                (java.util.concurrent.ScheduledFuture<?>) org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        when(chatRedisRepository.acquireLock(
                RedisConstants.PRESENCE_OPERATION_LOCK + "A001",
                RedisConstants.PRESENCE_OPERATION_LOCK_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        )).thenReturn("lock-token");
        when(chatRedisRepository.startLockRenewal(
                RedisConstants.PRESENCE_OPERATION_LOCK + "A001",
                "lock-token",
                RedisConstants.PRESENCE_OPERATION_LOCK_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        )).thenReturn((java.util.concurrent.ScheduledFuture) renewal);
        when(chatRedisRepository.sortedSetScore(RedisConstants.ONLINE_HEARTBEAT, "A001"))
                .thenReturn(0D);
        when(chatRedisRepository.sortedSetScore(RedisConstants.AGENT_LOAD, "A001"))
                .thenReturn(0D);
        when(chatSessionMapper.selectList(any())).thenReturn(List.of(session));

        service.handleHeartbeatTimeout("A001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> notice = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("U001"), eq("/queue/chat"), notice.capture()
        );
        assertEquals("AGENT_RECONNECTING", notice.getValue().get("event"));
        assertEquals("S001", notice.getValue().get("sessionId"));
        assertEquals(30L, notice.getValue().get("graceSeconds"));
    }

    @Test
    void typingEventIsStoredAndSentToTheOtherSessionParticipant() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        when(chatSessionMapper.selectById("S001")).thenReturn(session);

        service.handleTyping("S001", "U001", true);

        verify(chatRedisRepository).setValue(
                RedisConstants.SESSION_TYPING + "S001:U001", "1", 5, java.util.concurrent.TimeUnit.SECONDS
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(eq("A001"), eq("/queue/chat"), event.capture());
        assertEquals("TYPING", event.getValue().get("event"));
        assertEquals(true, event.getValue().get("typing"));
    }

    @Test
    void heartbeatBatchContinuesAfterOneUserFails() {
        ChatPresenceService presenceService = spy(service);
        when(chatRedisRepository.sortedSetRangeByScore(
                RedisConstants.ONLINE_HEARTBEAT, 0, 1000, 0, 100
        )).thenReturn(new LinkedHashSet<>(List.of("U001", "U002")));
        doThrow(new RuntimeException("boom")).when(presenceService).handleHeartbeatTimeout("U001");
        doNothing().when(presenceService).handleHeartbeatTimeout("U002");

        presenceService.handleExpiredHeartbeatUsers(1000, 100);

        verify(presenceService).handleHeartbeatTimeout("U002");
    }

    @Test
    void reconnectGraceBatchContinuesAfterOneAgentFails() {
        ChatPresenceService presenceService = spy(service);
        when(chatRedisRepository.sortedSetRangeByScore(
                RedisConstants.AGENT_RECONNECT_GRACE, 0, 1000, 0, 100
        )).thenReturn(new LinkedHashSet<>(List.of("A001", "A002")));
        doThrow(new RuntimeException("boom")).when(presenceService).handleAgentReconnectGraceTimeout("A001");
        doNothing().when(presenceService).handleAgentReconnectGraceTimeout("A002");

        presenceService.handleExpiredReconnectGracePeriods(1000, 100);

        verify(presenceService).handleAgentReconnectGraceTimeout("A002");
    }
}
