package com.example.customerservice.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMonitoringMetricsTest {

    @Test
    void duplicateDisconnectDoesNotMakeUserConnectionsNegative() {
        ChatMonitoringMetrics metrics = new ChatMonitoringMetrics(new SimpleMeterRegistry());

        metrics.registerConnection("ws-1", Set.of("USER"));
        metrics.unregisterConnection("ws-1");
        metrics.unregisterConnection("ws-1");

        assertEquals(0, metrics.userConnections());
    }

    @Test
    void lockFailureIsCountedAndLatencyIsRecorded() {
        ChatMonitoringMetrics metrics = new ChatMonitoringMetrics(new SimpleMeterRegistry());

        metrics.recordSessionLockAttempt(Duration.ofMillis(12), false);

        assertEquals(1, metrics.sessionLockFailureCount());
        assertEquals(1, metrics.sessionLockAttemptCount());
    }
}
