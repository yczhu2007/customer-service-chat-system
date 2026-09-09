package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.QueueTimeoutService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueTimeoutServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void oneUserFailureDoesNotStopLaterUser() {
        ChatRedisRepository redis = mock(ChatRedisRepository.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChatRoutingOperations routing = mock(ChatRoutingOperations.class);
        when(redis.sortedSetRange(RedisConstants.QUEUE_PENDING, 0, 99)).thenReturn(Set.of());
        when(redis.sortedSetRangeByScore(eq(RedisConstants.QUEUE_ENQUEUED_AT), eq(0D), anyDouble(), eq(0L), eq(100L)))
                .thenReturn(new LinkedHashSet<>(Set.of("U001", "U002")));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis unavailable"))
                .thenReturn(1L);

        QueueTimeoutService service = new QueueTimeoutService(redis, messaging, routing, 300, 120);

        assertDoesNotThrow(service::removeTimedOutUsers);
        verify(messaging).convertAndSendToUser(eq("U002"), eq("/queue/chat"), any());
        verify(routing).refreshWaitingPositions();
    }
}
