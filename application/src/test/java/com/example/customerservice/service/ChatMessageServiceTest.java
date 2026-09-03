package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.AssignResult;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatMessageService;
import com.example.customerservice.service.impl.ChatMessageDeliveryService;
import com.example.customerservice.service.impl.ChatMessageManagementService;
import com.example.customerservice.service.impl.ChatOfflineMessageService;
import com.example.customerservice.service.impl.ChatRoutingSessionService;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.core.task.TaskRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private ChatMessageReadMapper chatMessageReadMapper;
    @Mock private ChatAttachmentMapper chatAttachmentMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MessagePersistService messagePersistService;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private ChatPresenceOperations chatPresenceOperations;
    @Mock private ChatSessionTransferOperations chatSessionTransferOperations;
    @Mock private ChatSessionNotificationOperations chatSessionNotificationOperations;
    @Mock private ObjectProvider<ChatAgentOperations> agentOperationsProvider;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;

    private ChatMessageOperations service;
    private ChatRoutingOperations routingOperations;
    private ChatSessionOperations sessionOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        ChatRedisRepository chatRedisRepository = new ChatRedisRepository(redisTemplate);
        ChatMessageOperations messageOperations = newMessageOperations(
                chatRedisRepository, new ObjectMapper()
        );
        ChatRoutingSessionService routingSessionService = new ChatRoutingSessionService(
                chatRedisRepository, messageOperations, chatPresenceOperations,
                chatSessionTransferOperations, chatSessionNotificationOperations, chatSessionMapper,
                chatMessageMapper, chatMessageReadMapper, messagingTemplate,
                messagePersistService, new ObjectMapper(), sysUserRoleMapper,
                sysUserMapper,
                agentOperationsProvider,
                20, 1, 300, 1_000_000_000L, 120, 300, 200
        );
        service = messageOperations;
        routingOperations = routingSessionService;
        sessionOperations = routingSessionService;
    }

    @Test
    void markMessagesReadPersistsStateAndNotifiesCounterpart() {
        ChatSession session = activeSession();
        ChatMessage anchor = textMessage();
        when(chatSessionMapper.selectById("S001")).thenReturn(session);
        when(chatMessageMapper.selectById("M001")).thenReturn(anchor);
        when(chatMessageReadMapper.markReadThrough(
                eq("S001"), eq("U001"), eq("M001"), any(LocalDateTime.class)
        )).thenReturn(3);
        when(chatMessageReadMapper.countUnread("S001", "U001")).thenReturn(1L);

        MessageReadResult result = service.markMessagesRead("S001", "M001", "U001");

        assertEquals(3, result.markedCount());
        assertEquals(1L, result.unreadCount());
        verify(messagingTemplate).convertAndSendToUser(
                eq("A001"), eq("/queue/messages"), eq(result)
        );
    }

    @Test
    void markMessagesReadRejectsOwnMessageAsReadAnchor() {
        ChatSession session = activeSession();
        ChatMessage anchor = textMessage();
        anchor.setSenderId("U001");
        when(chatSessionMapper.selectById("S001")).thenReturn(session);
        when(chatMessageMapper.selectById("M001")).thenReturn(anchor);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.markMessagesRead("S001", "M001", "U001")
        );

        assertTrue(exception.getMessage().contains("会话对方"));
        verify(chatMessageReadMapper, never()).markReadThrough(
                anyString(), anyString(), anyString(), any(LocalDateTime.class)
        );
    }

    @Test
    void recallMessageUpdatesDatabaseAndNotifiesCounterpart() {
        ChatMessage message = textMessage();
        when(chatMessageMapper.selectById("M001")).thenReturn(message);
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(valueOperations.setIfAbsent(
                eq(RedisConstants.SESSION_OPERATION_LOCK + "S001"),
                anyString(),
                anyLong(),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(chatMessageMapper.recallOwnMessage(
                eq("M001"), eq("A001"),
                any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(1);
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());

        MessageMutationResult result = service.recallMessage("M001", "A001");

        assertEquals("MESSAGE_RECALLED", result.getEvent());
        assertTrue(result.isRecalled());
        verify(messagingTemplate).convertAndSendToUser(
                eq("U001"), eq("/queue/messages"), eq(result)
        );
    }

    @Test
    void recallMessageKeepsDatabaseResultWhenPostCommitCacheSyncFails() {
        ChatMessage message = textMessage();
        when(chatMessageMapper.selectById("M001")).thenReturn(message);
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(valueOperations.setIfAbsent(
                eq(RedisConstants.SESSION_OPERATION_LOCK + "S001"),
                anyString(), anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(chatMessageMapper.recallOwnMessage(
                eq("M001"), eq("A001"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(1);
        when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        assertDoesNotThrow(() -> service.recallMessage("M001", "A001"));
    }

    @Test
    void acceptedMessageKeepsDeduplicationWhenAsyncPersistenceQueueRejectsIt() {
        ChatMessage message = textMessage();
        message.setId(null);
        message.setClientMsgId("CLIENT-QUEUE-FULL");
        String dedupKey = RedisConstants.CLIENT_MSG_DEDUP + "S001:A001:CLIENT-QUEUE-FULL";
        when(valueOperations.setIfAbsent(eq(dedupKey), anyString(), eq(24L), eq(TimeUnit.HOURS)))
                .thenReturn(true);
        when(valueOperations.setIfAbsent(
                eq(RedisConstants.SESSION_OPERATION_LOCK + "S001"),
                anyString(), anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        doThrow(new TaskRejectedException("message persistence queue is full"))
                .when(messagePersistService).persistMessageAsync(any(ChatMessage.class));

        assertEquals(1, service.handleMessage(message));
        verify(redisTemplate, never()).delete(dedupKey);
    }

    @Test
    void pullOfflineMessagesPushesEveryCachedMessageWithoutDeletingBeforeAck()
            throws Exception {
        ChatMessage first = textMessage();
        ChatMessage second = textMessage();
        second.setId("M002");
        ObjectMapper mapper = new ObjectMapper();
        ChatRedisRepository chatRedisRepository = new ChatRedisRepository(redisTemplate);
        ChatMessageOperations messageOperations = newMessageOperations(
                chatRedisRepository, mapper
        );
        service = messageOperations;
        when(valueOperations.get(RedisConstants.USER_WS + "U001"))
                .thenReturn("WS001");
        when(listOperations.range(
                RedisConstants.OFFLINE_MSG + "U001", 0, -1
        )).thenReturn(List.of(
                mapper.writeValueAsString(first),
                mapper.writeValueAsString(second)
        ));

        service.pullOfflineMessages("U001");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, org.mockito.Mockito.times(3)).convertAndSendToUser(
                eq("U001"), eq("/queue/chat"), payload.capture()
        );
        assertEquals(3, payload.getAllValues().size());
        assertEquals(
                Map.of("event", "OFFLINE_MESSAGES_REPLAYED", "count", 2),
                payload.getAllValues().get(2)
        );
        verify(listOperations, never()).trim(anyString(), anyLong(), anyLong());
    }

    @Test
    void acknowledgeRemovesOnlyReceiversPendingCopyAndRecordsAck() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ChatMessage message = textMessage();
        String json = mapper.writeValueAsString(message);
        when(setOperations.isMember(RedisConstants.MSG_ACK + "M001", "U001"))
                .thenReturn(false);
        when(listOperations.range(
                RedisConstants.OFFLINE_MSG + "U001", 0, -1
        )).thenReturn(List.of(json));
        when(listOperations.remove(
                RedisConstants.OFFLINE_MSG + "U001", 1, json
        )).thenReturn(1L);

        service.handleAck("M001", "U001");

        verify(listOperations).remove(
                RedisConstants.OFFLINE_MSG + "U001", 1, json
        );
        verify(setOperations).add(
                RedisConstants.MSG_ACK + "M001", "U001"
        );
        verify(redisTemplate).expire(
                RedisConstants.MSG_ACK + "M001",
                RedisConstants.MSG_ACK_TTL_DAYS,
                TimeUnit.DAYS
        );
    }

    @Test
    void historyQueryUsesCursorAndIncludesUnreadCount() {
        ChatMessage message = textMessage();
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(chatMessageMapper.selectLatestHistory("S001", 11)).thenReturn(List.of(message));
        when(chatMessageMapper.selectCount(any())).thenReturn(21L);
        when(chatMessageReadMapper.countUnread("S001", "U001")).thenReturn(4L);

        ChatHistoryPage result = service.getHistory("S001", "U001", null, 10);

        assertEquals(1, result.records().size());
        assertEquals(21, result.total());
        assertEquals(10, result.pageSize());
        assertEquals(false, result.hasMore());
        assertEquals(4, result.unreadCount());
    }

    @Test
    void historyCursorUsesStrictCompositeCursorBoundary() {
        ChatMessage cursor = textMessage();
        cursor.setId("M010");
        ChatMessage sameTimeMessage = textMessage();
        sameTimeMessage.setId("M009");
        ChatMessage olderMessage = textMessage();
        olderMessage.setId("M008");
        olderMessage.setCreateTime(cursor.getCreateTime().minusSeconds(1));

        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(chatMessageMapper.selectById("M010")).thenReturn(cursor);
        when(chatMessageMapper.selectHistoryBeforeCursor(
                "S001", cursor.getCreateTime(), "M010", 3
        )).thenReturn(List.of(sameTimeMessage, olderMessage));
        when(chatMessageMapper.selectCount(any())).thenReturn(10L);
        when(chatMessageReadMapper.countUnread("S001", "U001")).thenReturn(0L);

        ChatHistoryPage result = service.getHistory("S001", "U001", "M010", 2);

        assertEquals(List.of("M008", "M009"), result.records().stream()
                .map(ChatMessage::getId)
                .toList());
        assertEquals(false, result.hasMore());
        verify(chatMessageMapper).selectHistoryBeforeCursor(
                "S001", cursor.getCreateTime(), "M010", 3
        );
        verify(chatMessageMapper, never()).selectList(any());
    }

    @Test
    void duplicateAcknowledgementUsesCompleteStoredMessage() {
        ChatMessage existingMessage = textMessage();
        existingMessage.setClientMsgId("CLIENT-001");
        ChatMessage duplicateRequest = textMessage();
        duplicateRequest.setId(null);
        duplicateRequest.setClientMsgId("CLIENT-001");

        String dedupKey = RedisConstants.CLIENT_MSG_DEDUP
                + "S001:A001:CLIENT-001";
        when(valueOperations.setIfAbsent(
                eq(dedupKey),
                anyString(),
                eq(24L),
                eq(TimeUnit.HOURS)
        )).thenReturn(false);
        when(valueOperations.get(dedupKey)).thenReturn("M001");
        when(chatMessageMapper.selectById("M001")).thenReturn(existingMessage);

        int result = service.handleMessage(duplicateRequest);

        assertEquals(0, result);
        ArgumentCaptor<ChatMessageDTO> acknowledgement =
                ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("A001"),
                eq("/queue/chat"),
                acknowledgement.capture()
        );
        assertEquals("DUPLICATE", acknowledgement.getValue().getAckStatus());
        assertEquals("M001", acknowledgement.getValue().getId());
        assertEquals("AGENT", acknowledgement.getValue().getSenderRole());
        assertEquals(existingMessage.getCreateTime(), acknowledgement.getValue().getCreateTime());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void findIdleAgentUsesVipCapacityAndLongestIdleTieBreakerAtomically() {
        SysUser vipUser = new SysUser();
        vipUser.setId("U001");
        vipUser.setVipLevel(3);
        when(sysUserMapper.selectById("U001")).thenReturn(vipUser);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()
        )).thenReturn("A002");

        String selectedAgent = routingOperations.findIdleAgent("U001");

        assertEquals("A002", selectedAgent);
        ArgumentCaptor<RedisScript> scriptCaptor =
                ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> argumentsCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                keysCaptor.capture(),
                argumentsCaptor.capture(), argumentsCaptor.capture(),
                argumentsCaptor.capture(), argumentsCaptor.capture(),
                argumentsCaptor.capture(), argumentsCaptor.capture()
        );
        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("load < selectedLoad"));
        assertTrue(script.contains("assignedAt < selectedAt"));
        assertTrue(keysCaptor.getValue().contains(RedisConstants.AGENT_SKILL_VIP));
        assertTrue(keysCaptor.getValue().contains(RedisConstants.AGENT_LAST_ASSIGNED));
        assertEquals("1", argumentsCaptor.getAllValues().get(3));
        assertEquals("1", argumentsCaptor.getAllValues().get(4));
    }

    @Test
    void failedAttachmentValidationRemovesTheDeduplicationKey() {
        ChatMessage message = textMessage();
        message.setId(null);
        message.setType("FILE");
        message.setContent("/chat/attachments/0123456789abcdef0123456789abcdef/content");
        message.setClientMsgId("CLIENT-FAIL");
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(24L), eq(TimeUnit.HOURS)))
                .thenReturn(true);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.handleMessage(message));

        verify(redisTemplate).delete(RedisConstants.CLIENT_MSG_DEDUP + "S001:A001:CLIENT-FAIL");
    }

    @Test
    void sessionCloseNotificationsWaitUntilTheDatabaseTransactionCommits() {
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(chatSessionMapper.endSession(eq("S001"), any(LocalDateTime.class))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        try {
            sessionOperations.endSessionByAgent("S001", "A001");

            verifyNoInteractions(chatSessionNotificationOperations);
            TransactionSynchronizationUtils.triggerAfterCommit();

            verify(chatSessionNotificationOperations).notifySessionClosed(
                    "U001", "S001", "MANUAL_END"
            );
            verify(chatSessionNotificationOperations).notifySessionClosed(
                    "A001", "S001", "MANUAL_END"
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void userEntersWaitingStateWhenNoAgentCanBeReserved() {
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                anyLong(),
                any(TimeUnit.class)
        )).thenReturn(true);

        AssignResult result = routingOperations.onUserConnected("U001");

        assertEquals(AssignResult.WAITING, result.getAssignmentStatus());
        assertEquals("WAITING_FOR_AGENT", result.getEvent());
        verify(chatSessionMapper).findActiveByUserId("U001");
    }

    private ChatMessageOperations newMessageOperations(
            ChatRedisRepository chatRedisRepository,
            ObjectMapper objectMapper
    ) {
        return new ChatMessageService(
                new ChatMessageDeliveryService(
                        chatRedisRepository, chatSessionMapper, chatMessageMapper,
                        chatMessageReadMapper, chatAttachmentMapper, messagingTemplate, messagePersistService,
                        objectMapper,
                        new ChatOfflineMessageService(
                                chatRedisRepository,
                                messagingTemplate,
                                objectMapper
                        ),
                        120, 300
                ),
                new ChatMessageManagementService(
                        chatRedisRepository, chatSessionMapper, chatMessageMapper,
                        chatMessageReadMapper, messagingTemplate, objectMapper, 120
                )
        );
    }

    private static ChatSession activeSession() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        session.setStatus("ACTIVE");
        session.setCreateTime(LocalDateTime.now().minusMinutes(1));
        return session;
    }

    private static ChatMessage textMessage() {
        ChatMessage message = new ChatMessage();
        message.setId("M001");
        message.setSessionId("S001");
        message.setSenderId("A001");
        message.setSenderRole("AGENT");
        message.setType("TEXT");
        message.setContent("原始内容");
        message.setCreateTime(LocalDateTime.now().minusSeconds(10));
        message.setRecalled(false);
        return message;
    }
}
