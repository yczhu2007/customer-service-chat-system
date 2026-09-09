package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.dto.SystemMonitoringSnapshotVO;
import com.example.customerservice.monitoring.ChatMonitoringMetrics;
import com.example.customerservice.repository.ChatRedisRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMonitoringServiceTest {

    @Test
    void redisFailureMarksOnlyRedisFieldsUnavailable() {
        ChatRedisRepository repository = mock(ChatRedisRepository.class);
        when(repository.sortedSetCardinality(RedisConstants.QUEUE_PENDING))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        ChatMonitoringMetrics metrics = new ChatMonitoringMetrics(new SimpleMeterRegistry());
        metrics.registerConnection("ws-1", Set.of("USER"));
        ChatMonitoringService service = new ChatMonitoringService(repository, metrics);

        SystemMonitoringSnapshotVO snapshot = service.findSnapshot();

        assertFalse(snapshot.redisAvailable());
        assertNull(snapshot.queueLength());
        assertNull(snapshot.deadLetterBacklog());
        assertEquals(1, snapshot.onlineConnections().user());
    }
}
