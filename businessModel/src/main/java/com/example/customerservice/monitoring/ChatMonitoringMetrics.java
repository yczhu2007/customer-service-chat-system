package com.example.customerservice.monitoring;

import com.example.customerservice.dto.SystemMonitoringSnapshotVO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

/** 聊天系统运行指标的本地采集器。 */
@Component
public class ChatMonitoringMetrics {

    private static final String USER = "USER";
    private static final String AGENT = "AGENT";

    private final MeterRegistry meterRegistry;
    private final Timer messagePersistenceTimer;
    private final Timer sessionLockTimer;
    private final Counter sessionLockFailures;
    private final AtomicInteger userConnections = new AtomicInteger();
    private final AtomicInteger agentConnections = new AtomicInteger();
    private final Map<String, ConnectionRole> connectionRoles = new ConcurrentHashMap<>();

    public ChatMonitoringMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.messagePersistenceTimer = Timer.builder("chat.message.persist.latency").register(meterRegistry);
        this.sessionLockTimer = Timer.builder("chat.redis.session.lock.acquire").register(meterRegistry);
        this.sessionLockFailures = Counter.builder("chat.redis.session.lock.acquire.failures").register(meterRegistry);
        Gauge.builder("chat.websocket.connections", userConnections, AtomicInteger::doubleValue)
                .tag("role", "user").register(meterRegistry);
        Gauge.builder("chat.websocket.connections", agentConnections, AtomicInteger::doubleValue)
                .tag("role", "agent").register(meterRegistry);
    }

    public void registerRedisGauge(String name, DoubleSupplier supplier) {
        Gauge.builder(name, supplier, DoubleSupplier::getAsDouble).register(meterRegistry);
    }

    public void recordMessagePersisted(Duration duration) {
        if (!duration.isNegative()) {
            messagePersistenceTimer.record(duration);
        }
    }

    public void recordSessionLockAttempt(Duration duration, boolean acquired) {
        if (!duration.isNegative()) {
            sessionLockTimer.record(duration);
        }
        if (!acquired) {
            sessionLockFailures.increment();
        }
    }

    public void registerConnection(String sessionId, Set<String> roleCodes) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ConnectionRole role = roleOf(roleCodes);
        if (role != null && connectionRoles.putIfAbsent(sessionId, role) == null) {
            counterFor(role).incrementAndGet();
        }
    }

    public void unregisterConnection(String sessionId) {
        ConnectionRole role = connectionRoles.remove(sessionId);
        if (role != null) {
            counterFor(role).updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    public long userConnections() {
        return userConnections.get();
    }

    public long agentConnections() {
        return agentConnections.get();
    }

    public long sessionLockFailureCount() {
        return (long) sessionLockFailures.count();
    }

    public long sessionLockAttemptCount() {
        return sessionLockTimer.count();
    }

    public SystemMonitoringSnapshotVO.OnlineConnections onlineConnections() {
        return new SystemMonitoringSnapshotVO.OnlineConnections(userConnections(), agentConnections());
    }

    public SystemMonitoringSnapshotVO.LatencySummary messagePersistenceLatency() {
        return summary(messagePersistenceTimer, 0L);
    }

    public SystemMonitoringSnapshotVO.LatencySummary sessionLockLatency() {
        return summary(sessionLockTimer, sessionLockFailureCount());
    }

    private SystemMonitoringSnapshotVO.LatencySummary summary(Timer timer, long failures) {
        long count = timer.count();
        if (count == 0) {
            return new SystemMonitoringSnapshotVO.LatencySummary(0, null, null, null, failures);
        }
        return new SystemMonitoringSnapshotVO.LatencySummary(
                count,
                timer.totalTime(TimeUnit.MILLISECONDS) / count,
                percentileMillis(timer),
                timer.max(TimeUnit.MILLISECONDS),
                failures
        );
    }

    private Double percentileMillis(Timer timer) {
        for (ValueAtPercentile percentile : timer.takeSnapshot().percentileValues()) {
            if (Double.compare(percentile.percentile(), 0.95D) == 0) {
                return percentile.value(TimeUnit.MILLISECONDS);
            }
        }
        return null;
    }

    private ConnectionRole roleOf(Set<String> roleCodes) {
        if (roleCodes == null) {
            return null;
        }
        if (roleCodes.contains(AGENT)) {
            return ConnectionRole.AGENT;
        }
        return roleCodes.contains(USER) ? ConnectionRole.USER : null;
    }

    private AtomicInteger counterFor(ConnectionRole role) {
        return role == ConnectionRole.AGENT ? agentConnections : userConnections;
    }

    private enum ConnectionRole {
        USER,
        AGENT
    }
}
