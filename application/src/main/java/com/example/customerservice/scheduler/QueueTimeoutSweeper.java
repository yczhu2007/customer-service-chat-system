package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatRoutingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueTimeoutSweeper {
    private final ChatRoutingOperations chatRoutingOperations;
    private final DistributedSchedulerLock schedulerLock;

    public QueueTimeoutSweeper(ChatRoutingOperations chatRoutingOperations,
                               DistributedSchedulerLock schedulerLock) {
        this.chatRoutingOperations = chatRoutingOperations;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.chat.queue.sweep-delay-ms:15000}")
    public void removeTimedOutUsers() {
        schedulerLock.execute("queue-timeout", chatRoutingOperations::removeTimedOutWaitingUsers);
    }
}
