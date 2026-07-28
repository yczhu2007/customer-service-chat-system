package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.MessagePersistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时补写异步落库失败的聊天消息。
 */
@Component
public class MessageReconciliationScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    MessageReconciliationScheduler.class
            );

    private final MessagePersistService
            messagePersistService;
    private final StringRedisTemplate redisTemplate;
    private final long alertThreshold;

    public MessageReconciliationScheduler(
            MessagePersistService
                    messagePersistService,
            StringRedisTemplate redisTemplate,
            @Value("${app.chat.persist.alert-threshold:20}")
            long alertThreshold
    ) {
        this.messagePersistService =
                messagePersistService;
        this.redisTemplate = redisTemplate;
        this.alertThreshold = alertThreshold;
    }

    @Scheduled(
            fixedDelay = 30_000
    )
    public void retryFailedMessages() {
        try {
            messagePersistService
                    .retryFailedMessages();
            checkBacklog();
        } catch (Exception exception) {
            LOGGER.error(
                    "定时补写聊天消息失败",
                    exception
            );
        }
    }

    private void checkBacklog() {
        Long pendingCount = redisTemplate.opsForZSet().zCard(
                RedisConstants.PERSIST_PENDING
        );
        Long deadLetterCount = redisTemplate.opsForZSet().zCard(
                RedisConstants.PERSIST_DEADLETTER
        );
        if ((pendingCount != null && pendingCount >= alertThreshold)
                || (deadLetterCount != null && deadLetterCount > 0)) {
            LOGGER.warn(
                    "消息持久化告警：pending={}, deadletter={}, threshold={}",
                    pendingCount,
                    deadLetterCount,
                    alertThreshold
            );
        }
    }
}
