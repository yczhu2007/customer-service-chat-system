package com.example.customerservice.scheduler;

import com.example.customerservice.service.impl.QueueTimeoutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueTimeoutSweeper {
    private final QueueTimeoutService queueTimeoutService;
    private final DistributedSchedulerLock schedulerLock;

    public QueueTimeoutSweeper(QueueTimeoutService queueTimeoutService,
                               DistributedSchedulerLock schedulerLock) {
        this.queueTimeoutService = queueTimeoutService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.chat.queue.sweep-delay-ms:15000}")
    public void removeTimedOutUsers() {
        schedulerLock.execute("queue-timeout", queueTimeoutService::removeTimedOutUsers);
    }
}
