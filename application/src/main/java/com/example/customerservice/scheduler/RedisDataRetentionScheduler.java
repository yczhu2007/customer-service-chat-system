package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.MessagePersistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 定期清理死信和VIP统计中的过期成员。 */
@Component
@Slf4j
public class RedisDataRetentionScheduler {

    private static final int CLEANUP_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;
    private final MessagePersistService messagePersistService;
    private final DistributedSchedulerLock schedulerLock;

    public RedisDataRetentionScheduler(
            StringRedisTemplate redisTemplate,
            MessagePersistService messagePersistService,
            DistributedSchedulerLock schedulerLock
    ) {
        this.redisTemplate = redisTemplate;
        this.messagePersistService = messagePersistService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.chat.redis-retention-sweep-delay-ms:3600000}")
    public void cleanupExpiredData() {
        schedulerLock.execute("redis-data-retention", this::cleanupExpiredDataLocked);
    }

    private void cleanupExpiredDataLocked() {
        long now = System.currentTimeMillis();
        long deadLetterCutoff = now - TimeUnit.DAYS.toMillis(
                RedisConstants.PERSIST_DEADLETTER_RETENTION_DAYS
        );
        int deadLetters = messagePersistService.cleanupExpiredDeadLetters(
                deadLetterCutoff,
                CLEANUP_BATCH_SIZE
        );
        long statsCutoff = now - TimeUnit.DAYS.toMillis(
                RedisConstants.VIP_STATS_RETENTION_DAYS
        );
        int waitStats = cleanupStatistics(
                RedisConstants.STATS_VIP_WAIT_CREATED_AT,
                RedisConstants.STATS_VIP_WAIT,
                statsCutoff
        );
        int resolveStats = cleanupStatistics(
                RedisConstants.STATS_VIP_RESOLVE_CREATED_AT,
                RedisConstants.STATS_VIP_RESOLVE,
                statsCutoff
        );
        if (deadLetters + waitStats + resolveStats > 0) {
            log.info(
                    "Redis过期数据清理完成，deadletters={}，vipWait={}，vipResolve={}",
                    deadLetters,
                    waitStats,
                    resolveStats
            );
        }
    }

    private int cleanupStatistics(
            String timestampKey,
            String valueKey,
            long cutoff
    ) {
        Set<String> expiredIds = redisTemplate.opsForZSet().rangeByScore(
                timestampKey,
                0,
                cutoff,
                0,
                CLEANUP_BATCH_SIZE
        );
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        Object[] ids = expiredIds.toArray();
        redisTemplate.opsForZSet().remove(timestampKey, ids);
        redisTemplate.opsForZSet().remove(valueKey, ids);
        return expiredIds.size();
    }
}
