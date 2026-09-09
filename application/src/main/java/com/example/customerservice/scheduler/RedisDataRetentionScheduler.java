package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.MessagePersistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 定期清理死信和VIP统计中的过期成员。 */
@Component
@Slf4j
public class RedisDataRetentionScheduler {

    private static final int CLEANUP_BATCH_SIZE = 500;

    private final MessagePersistService messagePersistService;
    private final DistributedSchedulerLock schedulerLock;

    public RedisDataRetentionScheduler(
            MessagePersistService messagePersistService,
            DistributedSchedulerLock schedulerLock
    ) {
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
        long statsCutoff = now - TimeUnit.DAYS.toMillis(RedisConstants.VIP_STATS_RETENTION_DAYS);
        int cleaned = messagePersistService.cleanupExpiredRedisData(deadLetterCutoff, statsCutoff, CLEANUP_BATCH_SIZE);
        if (cleaned > 0) {
            log.info(
                    "Redis过期数据清理完成，deadletters={}，vipWait={}，vipResolve={}",
                    cleaned,
                    0,
                    0
            );
        }
    }
}
