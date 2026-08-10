package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.IChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionInactivitySchedulerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private IChatService chatService;

    private SessionInactivityScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        scheduler = new SessionInactivityScheduler(redisTemplate, chatService, 60);
    }

    @Test
    void reassignsEveryExpiredSession() {
        when(zSetOperations.rangeByScore(
                eq(RedisConstants.SESSION_LAST_ACTIVITY),
                eq(0D),
                anyDouble(),
                eq(0L),
                eq(100L)
        )).thenReturn(new LinkedHashSet<>(Set.of("S001", "S002")));

        scheduler.reassignInactiveSessions();

        verify(chatService).handleSessionInactivityTimeout(eq("S001"), anyLong());
        verify(chatService).handleSessionInactivityTimeout(eq("S002"), anyLong());
    }

    @Test
    void doesNothingWhenNoSessionExpired() {
        when(zSetOperations.rangeByScore(
                eq(RedisConstants.SESSION_LAST_ACTIVITY),
                eq(0D),
                anyDouble(),
                eq(0L),
                eq(100L)
        )).thenReturn(Set.of());

        scheduler.reassignInactiveSessions();

        verify(chatService, never()).handleSessionInactivityTimeout(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
