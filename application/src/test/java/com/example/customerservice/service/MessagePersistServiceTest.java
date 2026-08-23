package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.service.impl.MessagePersistServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagePersistServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deadLetterReplayMovesMessageBackToPendingAtomically() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ChatMessage message = new ChatMessage();
        message.setId("M001");
        when(objectMapper.readValue("{message}", ChatMessage.class))
                .thenReturn(message);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn("{message}");
        MessagePersistServiceImpl service = new MessagePersistServiceImpl(
                mock(com.example.customerservice.mapper.ChatMessageMapper.class),
                redisTemplate,
                objectMapper,
                mock(SimpMessagingTemplate.class),
                executor
        );

        service.replayDeadLetter("M001");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString()
        );
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void markPendingFailsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis unavailable"));
        ChatMessage message = new ChatMessage();
        message.setId("M001");
        MessagePersistServiceImpl service = new MessagePersistServiceImpl(
                mock(com.example.customerservice.mapper.ChatMessageMapper.class),
                redisTemplate,
                new ObjectMapper(),
                mock(SimpMessagingTemplate.class),
                mock(ThreadPoolTaskExecutor.class)
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.markPending(message)
        );
    }

    @Test
    void duplicateKeyWithDifferentPersistedMessageMovesRequestToDeadLetter() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> values = mock(org.springframework.data.redis.core.ValueOperations.class);
        org.springframework.data.redis.core.ZSetOperations<String, String> sortedSet = mock(org.springframework.data.redis.core.ZSetOperations.class);
        com.example.customerservice.mapper.ChatMessageMapper mapper = mock(com.example.customerservice.mapper.ChatMessageMapper.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForZSet()).thenReturn(sortedSet);
        when(values.setIfAbsent(anyString(), anyString(), any(Long.class), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);

        ChatMessage request = new ChatMessage();
        request.setId("M-request"); request.setSessionId("S001"); request.setSenderId("U001");
        request.setClientMsgId("C001"); request.setType("TEXT"); request.setContent("expected");
        ChatMessage conflicting = new ChatMessage();
        conflicting.setId("M-existing"); conflicting.setSessionId("S001"); conflicting.setSenderId("U001");
        conflicting.setClientMsgId("C001"); conflicting.setType("TEXT"); conflicting.setContent("different");
        when(mapper.insert(request)).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        when(mapper.findByClientMessage("S001", "U001", "C001")).thenReturn(conflicting);

        MessagePersistServiceImpl service = new MessagePersistServiceImpl(
                mapper, redisTemplate, new ObjectMapper(), messaging, mock(ThreadPoolTaskExecutor.class)
        );
        service.persistMessageAsync(request);

        verify(sortedSet).add(org.mockito.ArgumentMatchers.eq(RedisConstants.PERSIST_DEADLETTER),
                org.mockito.ArgumentMatchers.eq("M-request"), org.mockito.ArgumentMatchers.any(Double.class));
        verify(messaging, org.mockito.Mockito.never()).convertAndSendToUser(
                anyString(), anyString(), any()
        );
    }
}
