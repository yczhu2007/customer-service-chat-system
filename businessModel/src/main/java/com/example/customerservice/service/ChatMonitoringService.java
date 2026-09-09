package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.dto.SystemMonitoringSnapshotVO;
import com.example.customerservice.monitoring.ChatMonitoringMetrics;
import com.example.customerservice.repository.ChatRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 汇总管理员监控快照；不保存历史数据。 */
@Service
@Slf4j
public class ChatMonitoringService {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatMonitoringMetrics metrics;

    public ChatMonitoringService(
            ChatRedisRepository chatRedisRepository,
            ChatMonitoringMetrics metrics
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.metrics = metrics;
        metrics.registerRedisGauge("chat.queue.pending", () -> cardinalityForGauge(RedisConstants.QUEUE_PENDING));
        metrics.registerRedisGauge("chat.message.persist.deadletter", () -> cardinalityForGauge(RedisConstants.PERSIST_DEADLETTER));
    }

    public SystemMonitoringSnapshotVO findSnapshot() {
        Long queueLength = null;
        Long deadLetterBacklog = null;
        boolean redisAvailable = true;
        try {
            queueLength = normalizedCardinality(RedisConstants.QUEUE_PENDING);
            deadLetterBacklog = normalizedCardinality(RedisConstants.PERSIST_DEADLETTER);
        } catch (RuntimeException exception) {
            redisAvailable = false;
            log.warn("读取系统监控 Redis 指标失败", exception);
        }
        return new SystemMonitoringSnapshotVO(
                redisAvailable,
                queueLength,
                deadLetterBacklog,
                metrics.onlineConnections(),
                metrics.messagePersistenceLatency(),
                metrics.sessionLockLatency(),
                Instant.now()
        );
    }

    private double cardinalityForGauge(String key) {
        try {
            return normalizedCardinality(key);
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }

    private long normalizedCardinality(String key) {
        Long value = chatRedisRepository.sortedSetCardinality(key);
        return value == null ? 0L : value;
    }
}
