package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.service.MessagePersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 聊天消息异步落库服务。
 *
 * 首次落库失败时在当前异步线程中进行有限重试；
 * 多次失败后保存到Redis，等待定时任务继续补写。
 */
@Service
public class MessagePersistServiceImpl
        implements MessagePersistService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    MessagePersistServiceImpl.class
            );

    private static final int MAX_ATTEMPTS = 3;

    private static final long RETRY_DELAY_MILLIS =
            1_000L;

    private final ChatMessageMapper chatMessageMapper;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public MessagePersistServiceImpl(
            ChatMessageMapper chatMessageMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.chatMessageMapper =
                chatMessageMapper;
        this.redisTemplate =
                redisTemplate;
        this.objectMapper =
                objectMapper;
    }

    @Override
    @Async
    public void persistMessageAsync(
            ChatMessage message
    ) {
        if (!persistWithRetry(message)) {
            saveFailedMessage(message);
        }
    }

    @Override
    public void retryFailedMessages() {
        Map<Object, Object> failedMessages =
                redisTemplate.opsForHash()
                        .entries(
                                RedisConstants
                                        .PERSIST_FAILED
                        );

        if (failedMessages.isEmpty()) {
            return;
        }

        for (
                Map.Entry<Object, Object> entry
                : failedMessages.entrySet()
        ) {
            String messageId =
                    String.valueOf(
                            entry.getKey()
                    );

            try {
                ChatMessage message =
                        objectMapper.readValue(
                                String.valueOf(
                                        entry.getValue()
                                ),
                                ChatMessage.class
                        );

                if (persistWithRetry(message)) {
                    removeFailedMessage(
                            messageId
                    );
                }
            } catch (Exception exception) {
                LOGGER.error(
                        "解析待重试消息失败，messageId={}",
                        messageId,
                        exception
                );
            }
        }
    }

    private boolean persistWithRetry(
            ChatMessage message
    ) {
        if (
                message == null ||
                        message.getId() == null ||
                        message.getId().isBlank()
        ) {
            LOGGER.error(
                    "消息落库失败：消息或消息ID为空"
            );
            return false;
        }

        for (
                int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++
        ) {
            try {
                int insertedRows =
                        chatMessageMapper.insert(
                                message
                        );

                if (insertedRows != 1) {
                    throw new IllegalStateException(
                            "消息写入行数不是1"
                    );
                }

                removeFailedMessage(
                        message.getId()
                );

                LOGGER.info(
                        "聊天消息落库成功，messageId={}",
                        message.getId()
                );

                return true;
            } catch (
                    DuplicateKeyException exception
            ) {
                /*
                 * 主键或clientMsgId重复说明该消息已经成功写入，
                 * 直接移除失败记录，保证重试幂等。
                 */
                removeFailedMessage(
                        message.getId()
                );

                LOGGER.info(
                        "聊天消息已经存在，按落库成功处理，messageId={}",
                        message.getId()
                );

                return true;
            } catch (Exception exception) {
                LOGGER.warn(
                        "聊天消息落库失败，第{}次尝试，messageId={}",
                        attempt,
                        message.getId(),
                        exception
                );

                if (attempt < MAX_ATTEMPTS) {
                    if (!waitBeforeRetry()) {
                        return false;
                    }
                }
            }
        }

        return false;
    }

    private boolean waitBeforeRetry() {
        try {
            Thread.sleep(
                    RETRY_DELAY_MILLIS
            );
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            LOGGER.warn(
                    "消息落库重试线程被中断"
            );

            return false;
        }
    }

    private void saveFailedMessage(
            ChatMessage message
    ) {
        if (
                message == null ||
                        message.getId() == null ||
                        message.getId().isBlank()
        ) {
            return;
        }

        try {
            String messageJson =
                    objectMapper.writeValueAsString(
                            message
                    );

            redisTemplate.opsForHash()
                    .put(
                            RedisConstants
                                    .PERSIST_FAILED,
                            message.getId(),
                            messageJson
                    );

            LOGGER.error(
                    "聊天消息多次落库失败，已保存到Redis等待重试，messageId={}",
                    message.getId()
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "保存落库失败消息到Redis时发生异常，messageId={}",
                    message.getId(),
                    exception
            );
        }
    }

    private void removeFailedMessage(
            String messageId
    ) {
        if (
                messageId == null ||
                        messageId.isBlank()
        ) {
            return;
        }

        redisTemplate.opsForHash()
                .delete(
                        RedisConstants.PERSIST_FAILED,
                        messageId
                );
    }
}
