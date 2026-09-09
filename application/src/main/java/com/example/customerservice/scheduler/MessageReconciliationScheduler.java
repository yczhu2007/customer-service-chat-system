package com.example.customerservice.scheduler;

import com.example.customerservice.service.MessagePersistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时补写异步落库失败的聊天消息。
 */
@Component
@Slf4j
public class MessageReconciliationScheduler {

    private final MessagePersistService
            messagePersistService;
    private final long alertThreshold;
    private final DistributedSchedulerLock schedulerLock;

    public MessageReconciliationScheduler(
            MessagePersistService
                    messagePersistService,
            DistributedSchedulerLock schedulerLock,
            @Value("${app.chat.persist.alert-threshold:20}")
            long alertThreshold
    ) {
        this.messagePersistService =
                messagePersistService;
        this.schedulerLock = schedulerLock;
        this.alertThreshold = alertThreshold;
    }

    @Scheduled(fixedDelayString = "${app.chat.persist.reconciliation-delay-ms:30000}")
    public void retryFailedMessages() {
        schedulerLock.execute(
                "message-reconciliation",
                this::retryFailedMessagesLocked
        );
    }

    private void retryFailedMessagesLocked() {
        try {
            messagePersistService.retryAndCheckBacklog(alertThreshold);
        } catch (Exception exception) {
            log.error(
                    "定时补写聊天消息失败",
                    exception
            );
        }
    }

}
