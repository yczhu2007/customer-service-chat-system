package com.example.customerservice.scheduler;

import com.example.customerservice.service.MessagePersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时补写异步落库失败的聊天消息。
 */
@Component
public class MessagePersistRetryScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    MessagePersistRetryScheduler.class
            );

    private final MessagePersistService
            messagePersistService;

    public MessagePersistRetryScheduler(
            MessagePersistService
                    messagePersistService
    ) {
        this.messagePersistService =
                messagePersistService;
    }

    @Scheduled(
            fixedDelay = 30_000
    )
    public void retryFailedMessages() {
        try {
            messagePersistService
                    .retryFailedMessages();
        } catch (Exception exception) {
            LOGGER.error(
                    "定时补写聊天消息失败",
                    exception
            );
        }
    }
}
