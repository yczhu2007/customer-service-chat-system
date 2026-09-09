package com.example.customerservice.repository;

import com.example.customerservice.monitoring.ChatMonitoringMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRedisRepositoryTest {

    @Test
    void failedSessionLockAttemptIsRecordedWithoutChangingResult() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> values = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(false);
        ChatMonitoringMetrics metrics = new ChatMonitoringMetrics(new SimpleMeterRegistry());
        ChatRedisRepository repository = new ChatRedisRepository(template, metrics);

        assertNull(repository.acquireSessionOperationLock("S001"));
        assertEquals(1, metrics.sessionLockFailureCount());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void renewLockUsesOwnerTokenAndConfiguredTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        ChatRedisRepository repository = new ChatRedisRepository(template, new ChatMonitoringMetrics(new SimpleMeterRegistry()));

        assertTrue(repository.renewLock("session:operation:lock:S001", "owner-a", 30));

        verify(template).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("session:operation:lock:S001")),
                org.mockito.ArgumentMatchers.eq("owner-a"),
                org.mockito.ArgumentMatchers.eq("30")
        );
    }

    @Test
    void renewLockRejectsExpiredOrInvalidLeaseArguments() {
        ChatRedisRepository repository = new ChatRedisRepository(mock(StringRedisTemplate.class), new ChatMonitoringMetrics(new SimpleMeterRegistry()));

        assertFalse(repository.renewLock("key", "token", 0));
        assertFalse(repository.renewLock("key", null, 30));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cancelQueueRemovesTheEnqueueTimestampFromItsSortedSet() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        ChatRedisRepository repository = new ChatRedisRepository(template, new ChatMonitoringMetrics(new SimpleMeterRegistry()));

        repository.cancelQueueEntry("U001");

        org.mockito.ArgumentCaptor<RedisScript> script = org.mockito.ArgumentCaptor.forClass(RedisScript.class);
        verify(template).execute(script.capture(), eq(List.of(
                "queue:pending", "queue:enqueued-at", "queue:vip-level", "queue:normal-due", "vip:callback:pending"
        )), eq("U001"));
        assertTrue(script.getValue().getScriptAsString().contains("ZREM', KEYS[4]"));
    }
}
