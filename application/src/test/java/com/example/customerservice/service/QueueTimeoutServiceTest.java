package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.impl.QueueTimeoutService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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
    void notificationFailureDoesNotStopLaterUser() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChatRoutingOperations routing = mock(ChatRoutingOperations.class);
        when(redis.opsForZSet()).thenReturn(zSet);
        when(zSet.range(RedisConstants.QUEUE_PENDING, 0, 99)).thenReturn(Set.of());
        when(zSet.rangeByScore(eq(RedisConstants.QUEUE_ENQUEUED_AT), eq(0D), anyDouble(), eq(0L), eq(100L)))
                .thenReturn(new LinkedHashSet<>(Set.of("U001", "U002")));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        doThrow(new RuntimeException("broker unavailable"))
                .when(messaging).convertAndSendToUser(eq("U001"), eq("/queue/chat"), any());

        QueueTimeoutService service = new QueueTimeoutService(redis, messaging, routing, 300, 120);

        assertDoesNotThrow(service::removeTimedOutUsers);
        verify(messaging).convertAndSendToUser(eq("U002"), eq("/queue/chat"), any());
        verify(routing).refreshWaitingPositions();
    }
}
