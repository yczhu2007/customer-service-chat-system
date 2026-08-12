package com.example.customerservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatServiceImpl;
import com.example.customerservice.service.impl.ChatMessageService;
import com.example.customerservice.service.impl.ChatMessageDeliveryService;
import com.example.customerservice.service.impl.ChatMessageManagementService;
import com.example.customerservice.service.impl.ChatOfflineMessageService;
import com.example.customerservice.service.impl.ChatRoutingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplMessageTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private ChatMessageReadMapper chatMessageReadMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MessagePersistService messagePersistService;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private ChatPresenceOperations chatPresenceOperations;
    @Mock private ChatSessionTransferOperations chatSessionTransferOperations;
    @Mock private ChatSessionNotificationOperations chatSessionNotificationOperations;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;

    private ChatServiceImpl service;

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
                sysUserMapper, 20, 1, 300, 1_000_000_000L, 120, 300
        );
        service = new ChatServiceImpl(routingSessionService);
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
    void editMessageUpdatesDatabaseAndNotifiesCounterpart() {
        ChatMessage message = textMessage();
        when(chatMessageMapper.selectById("M001")).thenReturn(message);
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(valueOperations.setIfAbsent(
                eq(RedisConstants.SESSION_OPERATION_LOCK + "S001"),
                anyString(),
                anyLong(),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(chatMessageMapper.editOwnMessage(
                eq("M001"), eq("A001"), eq("修改后的内容"),
                any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(1);
        when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());

        MessageMutationResult result = service.editMessage(
                "M001", "修改后的内容", "A001"
        );

        assertEquals("MESSAGE_EDITED", result.getEvent());
        assertEquals("修改后的内容", result.getContent());
        verify(messagingTemplate).convertAndSendToUser(
                eq("U001"), eq("/queue/messages"), eq(result)
        );
    }

    @Test
    void editMessageRejectsExpiredMessage() {
        ChatMessage message = textMessage();
        message.setCreateTime(LocalDateTime.now().minusMinutes(10));
        when(chatMessageMapper.selectById("M001")).thenReturn(message);
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.editMessage("M001", "修改后的内容", "A001")
        );

        assertTrue(exception.getMessage().contains("超过允许编辑"));
        verify(chatMessageMapper, never()).editOwnMessage(
                anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class)
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
        ChatRoutingSessionService routingSessionService = new ChatRoutingSessionService(
                chatRedisRepository, messageOperations, chatPresenceOperations,
                chatSessionTransferOperations, chatSessionNotificationOperations, chatSessionMapper,
                chatMessageMapper, chatMessageReadMapper, messagingTemplate,
                messagePersistService, mapper, sysUserRoleMapper, sysUserMapper,
                20, 1, 300, 1_000_000_000L, 120, 300
        );
        service = new ChatServiceImpl(routingSessionService);
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
        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                eq("U001"), eq("/queue/chat"), payload.capture()
        );
        assertEquals(2, payload.getAllValues().size());
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
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyQueryIsPagedAndIncludesUnreadCount() {
        ChatMessage message = textMessage();
        Page<ChatMessage> databasePage = new Page<>(2, 10, 21);
        databasePage.setRecords(List.of(message));
        when(chatSessionMapper.selectById("S001")).thenReturn(activeSession());
        when(chatMessageMapper.selectPage(any(Page.class), any()))
                .thenReturn(databasePage);
        when(chatMessageReadMapper.countUnread("S001", "U001")).thenReturn(4L);

        ChatHistoryPage result = service.getHistory("S001", "U001", 2, 10);

        assertEquals(1, result.records().size());
        assertEquals(21, result.total());
        assertEquals(2, result.pageNo());
        assertEquals(10, result.pageSize());
        assertEquals(3, result.pages());
        assertEquals(4, result.unreadCount());
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

        String selectedAgent = service.findIdleAgent("U001");

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

    private ChatMessageOperations newMessageOperations(
            ChatRedisRepository chatRedisRepository,
            ObjectMapper objectMapper
    ) {
        return new ChatMessageService(
                new ChatMessageDeliveryService(
                        chatRedisRepository, chatSessionMapper, chatMessageMapper,
                        chatMessageReadMapper, messagingTemplate, messagePersistService,
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
                        chatMessageReadMapper, messagingTemplate, objectMapper, 120, 300
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
        message.setEdited(false);
        message.setRecalled(false);
        return message;
    }
}
