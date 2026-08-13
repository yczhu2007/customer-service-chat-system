package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.service.MessagePersistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 消息写入 MySQL 的可靠异步服务。
 * Redis 待确认记录在提交异步任务前写入，只有 MySQL 成功后才移除。
 */
@Service
@Slf4j
public class MessagePersistServiceImpl implements MessagePersistService {

    /** 首次执行 + 3 次指数退避重试。 */
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] RETRY_DELAYS_SECONDS = {10L, 30L, 60L};
    private static final DefaultRedisScript<Long> MARK_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1]); " +
                            "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); " +
                            "return 1;",
                    Long.class
            );

    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor messagePersistExecutor;

    public MessagePersistServiceImpl(
            ChatMessageMapper chatMessageMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("messagePersistExecutor")
            ThreadPoolTaskExecutor messagePersistExecutor
    ) {
        this.chatMessageMapper = chatMessageMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.messagePersistExecutor = messagePersistExecutor;
    }

    @Override
    public void markPending(ChatMessage message) {
        validateMessage(message);
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.execute(
                    MARK_PENDING_SCRIPT,
                    List.of(
                            RedisConstants.PERSIST_PENDING_PAYLOAD + message.getId(),
                            RedisConstants.PERSIST_PENDING
                    ),
                    payload,
                    message.getId(),
                    String.valueOf(System.currentTimeMillis())
            );
        } catch (Exception exception) {
            throw new IllegalStateException("记录待落库消息失败", exception);
        }
    }

    @Override
    @Async("messagePersistExecutor")
    public void persistMessageAsync(ChatMessage message) {
        if (!tryAcquireRetryLease(message.getId())) {
            return;
        }
        try {
            persistWithRetry(message);
        } finally {
            releaseRetryLease(message.getId());
        }
    }

    @Override
    public void retryFailedMessages() {
        Set<String> pendingIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.PERSIST_PENDING,
                0,
                System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10),
                0,
                100
        );
        if (pendingIds == null || pendingIds.isEmpty()) {
            return;
        }
        for (String messageId : pendingIds) {
            try {
                if (!tryAcquireRetryLease(messageId)) {
                    continue;
                }
                String payload = redisTemplate.opsForValue().get(
                        RedisConstants.PERSIST_PENDING_PAYLOAD + messageId
                );
                if (payload == null || payload.isBlank()) {
                    moveToDeadLetter(messageId);
                    releaseRetryLease(messageId);
                    continue;
                }
                ChatMessage message = objectMapper.readValue(payload, ChatMessage.class);
                messagePersistExecutor.execute(() -> {
                    try {
                        persistWithRetry(message);
                    } finally {
                        releaseRetryLease(message.getId());
                    }
                });
            } catch (Exception exception) {
                releaseRetryLease(messageId);
                log.error("重新提交待落库消息失败，messageId={}", messageId, exception);
            }
        }
    }

    private boolean persistWithRetry(ChatMessage message) {
        validateMessage(message);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                int insertedRows = chatMessageMapper.insert(message);
                if (insertedRows != 1) {
                    throw new IllegalStateException("消息写入行数不是 1");
                }
                markStored(message);
                return true;
            } catch (DuplicateKeyException exception) {
                // 主键或 clientMsgId 重复代表上次写入已经成功，按成功处理。
                markStored(message);
                return true;
            } catch (Exception exception) {
                recordRetryAttempt(message.getId());
                log.warn("消息落库失败，第 {} 次尝试，messageId={}", attempt, message.getId(), exception);
                if (attempt < MAX_ATTEMPTS && !waitBeforeRetry(attempt)) {
                    return false;
                }
            }
        }
        moveToDeadLetter(message.getId());
        return false;
    }

    private boolean waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_DELAYS_SECONDS[attempt - 1]));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void recordRetryAttempt(String messageId) {
        String retryCountKey = RedisConstants.PERSIST_RETRY_COUNT + messageId;
        redisTemplate.opsForValue().increment(retryCountKey);
        redisTemplate.expire(retryCountKey, 7, TimeUnit.DAYS);
    }

    private void markStored(ChatMessage message) {
        String messageId = message.getId();
        redisTemplate.opsForZSet().remove(RedisConstants.PERSIST_PENDING, messageId);
        redisTemplate.delete(RedisConstants.PERSIST_PENDING_PAYLOAD + messageId);
        redisTemplate.delete(RedisConstants.PERSIST_RETRY_COUNT + messageId);
        releaseRetryLease(messageId);
        ChatMessageDTO acknowledgement = ChatMessageDTO.fromEntity(message);
        acknowledgement.setAckStatus("STORED");
        messagingTemplate.convertAndSendToUser(message.getSenderId(), "/queue/chat", acknowledgement);
    }

    private void moveToDeadLetter(String messageId) {
        redisTemplate.opsForZSet().add(
                RedisConstants.PERSIST_DEADLETTER,
                messageId,
                System.currentTimeMillis()
        );
        redisTemplate.opsForZSet().remove(RedisConstants.PERSIST_PENDING, messageId);
        releaseRetryLease(messageId);
        log.error("消息达到最大落库重试次数，已转入死信集合，messageId={}", messageId);
    }

    private void validateMessage(ChatMessage message) {
        if (message == null || message.getId() == null || message.getId().isBlank()) {
            throw new IllegalArgumentException("消息或消息 ID 不能为空");
        }
    }

    private void releaseRetryLease(String messageId) {
        if (messageId != null && !messageId.isBlank()) {
            redisTemplate.delete(RedisConstants.PERSIST_RETRY_LEASE + messageId);
        }
    }

    private boolean tryAcquireRetryLease(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                RedisConstants.PERSIST_RETRY_LEASE + messageId,
                "1",
                RedisConstants.PERSIST_RETRY_LEASE_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(acquired);
    }
}
