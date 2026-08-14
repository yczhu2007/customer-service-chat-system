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
}
