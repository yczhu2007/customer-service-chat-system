package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.ChatMaintenanceOperations;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class SessionInactivitySchedulerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private ChatMaintenanceOperations chatMaintenanceOperations;
    @Mock private DistributedSchedulerLock schedulerLock;

    private SessionInactivityScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(schedulerLock).execute(anyString(), any(Runnable.class));
        scheduler = new SessionInactivityScheduler(
                redisTemplate,
                chatMaintenanceOperations,
                schedulerLock,
                60
        );
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

        verify(chatMaintenanceOperations).handleSessionInactivityTimeout(eq("S001"), anyLong());
        verify(chatMaintenanceOperations).handleSessionInactivityTimeout(eq("S002"), anyLong());
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

        verify(chatMaintenanceOperations, never()).handleSessionInactivityTimeout(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
