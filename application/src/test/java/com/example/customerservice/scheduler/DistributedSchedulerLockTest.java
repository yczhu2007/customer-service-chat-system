package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.repository.ChatRedisRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedSchedulerLockTest {

    @Test
    void executesAndReleasesTaskOnlyAfterRedisLockIsAcquired() {
        ChatRedisRepository repository = mock(ChatRedisRepository.class);
        when(repository.acquireLock(
                RedisConstants.SCHEDULER_LOCK + "task",
                RedisConstants.SCHEDULER_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        )).thenReturn("LOCK");
        DistributedSchedulerLock lock = new DistributedSchedulerLock(repository);
        AtomicBoolean executed = new AtomicBoolean();

        assertTrue(lock.execute("task", () -> executed.set(true)));

        assertTrue(executed.get());
        verify(repository).releaseLock(
                RedisConstants.SCHEDULER_LOCK + "task",
                "LOCK"
        );
    }

    @Test
    void skipsTaskWhenAnotherInstanceOwnsRedisLock() {
        ChatRedisRepository repository = mock(ChatRedisRepository.class);
        DistributedSchedulerLock lock = new DistributedSchedulerLock(repository);
        AtomicBoolean executed = new AtomicBoolean();

        assertFalse(lock.execute("task", () -> executed.set(true)));

        assertFalse(executed.get());
    }
}
