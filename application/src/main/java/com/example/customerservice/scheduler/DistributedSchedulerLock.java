package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.repository.ChatRedisRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 保证同一个定时任务在多实例环境中同一时刻只由一个实例执行。 */
@Component
@Slf4j
public class DistributedSchedulerLock {

    private final ChatRedisRepository chatRedisRepository;
    private final ScheduledExecutorService lockWatchdog =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "scheduler-lock-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    public DistributedSchedulerLock(ChatRedisRepository chatRedisRepository) {
        this.chatRedisRepository = chatRedisRepository;
    }

    public boolean execute(String taskName, Runnable task) {
        String lockKey = RedisConstants.SCHEDULER_LOCK + taskName;
        String lockToken = chatRedisRepository.acquireLock(
                lockKey,
                RedisConstants.SCHEDULER_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        if (lockToken == null) {
            return false;
        }
        long renewalIntervalSeconds = Math.max(
                1L,
                RedisConstants.SCHEDULER_LOCK_TTL_SECONDS / 3L
        );
        ScheduledFuture<?> renewalTask = lockWatchdog.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!chatRedisRepository.renewLock(
                                lockKey,
                                lockToken,
                                RedisConstants.SCHEDULER_LOCK_TTL_SECONDS
                        )) {
                            log.warn("定时任务分布式锁续期失败或锁已失效，taskName={}", taskName);
                        }
                    } catch (RuntimeException exception) {
                        log.error("定时任务分布式锁续期异常，taskName={}", taskName, exception);
                    }
                },
                renewalIntervalSeconds,
                renewalIntervalSeconds,
                TimeUnit.SECONDS
        );
        try {
            task.run();
            return true;
        } finally {
            renewalTask.cancel(false);
            chatRedisRepository.releaseLock(lockKey, lockToken);
        }
    }

    @PreDestroy
    public void shutdownWatchdog() {
        lockWatchdog.shutdownNow();
    }
}
