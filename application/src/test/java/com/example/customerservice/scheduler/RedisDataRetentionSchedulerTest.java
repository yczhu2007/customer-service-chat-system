package com.example.customerservice.scheduler;

import com.example.customerservice.service.MessagePersistService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisDataRetentionSchedulerTest {

    @Test
    void delegatesCurrentTimeAndBatchSizeThroughDistributedLock() {
        MessagePersistService service = mock(MessagePersistService.class);
        DistributedSchedulerLock lock = mock(DistributedSchedulerLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(eq("redis-data-retention"), any(Runnable.class));

        new RedisDataRetentionScheduler(service, lock).cleanupExpiredData();

        verify(service).cleanupExpiredRedisData(anyLong(), eq(500));
    }
}
