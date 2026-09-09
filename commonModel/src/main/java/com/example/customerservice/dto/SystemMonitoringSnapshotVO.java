package com.example.customerservice.dto;

import java.time.Instant;

/** 管理员读取的实时系统监控快照。 */
public record SystemMonitoringSnapshotVO(
        boolean redisAvailable,
        Long queueLength,
        Long deadLetterBacklog,
        OnlineConnections onlineConnections,
        LatencySummary messagePersistenceLatency,
        LatencySummary sessionLockLatency,
        Instant collectedAt
) {
    public record OnlineConnections(long user, long agent) { }

    public record LatencySummary(
            long count,
            Double averageMillis,
            Double p95Millis,
            Double maxMillis,
            long failureCount
    ) { }
}
