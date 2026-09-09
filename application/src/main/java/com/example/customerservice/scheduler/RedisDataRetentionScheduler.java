package com.example.customerservice.scheduler;

import com.example.customerservice.service.MessagePersistService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期清理死信和VIP统计中的过期成员。 */
@Component
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
        messagePersistService.cleanupExpiredRedisData(
                System.currentTimeMillis(), CLEANUP_BATCH_SIZE
        );
    }
}
