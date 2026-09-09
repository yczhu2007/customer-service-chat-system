package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import jakarta.annotation.PreDestroy;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.dto.DeadLetterMessageVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.monitoring.ChatMonitoringMetrics;
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
import java.util.ArrayList;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.UUID;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * 消息写入 MySQL 的可靠异步服务。
 * Redis 待确认记录在提交异步任务前写入，只有 MySQL 成功后才移除。
 */
@Service
@Slf4j
public class MessagePersistServiceImpl implements MessagePersistService {

    @Override
    public void retryAndCheckBacklog(long alertThreshold) {
        retryFailedMessages();
        Long pendingCount = redisTemplate.opsForZSet().zCard(RedisConstants.PERSIST_PENDING);
        Long deadLetterCount = redisTemplate.opsForZSet().zCard(RedisConstants.PERSIST_DEADLETTER);
        if ((pendingCount != null && pendingCount >= alertThreshold)
                || (deadLetterCount != null && deadLetterCount > 0)) {
            log.warn("消息持久化告警：pending={}, deadletter={}, threshold={}",
                    pendingCount, deadLetterCount, alertThreshold);
        }
    }

    @PreDestroy
    public void shutdownRetryLeaseWatchdog() {
        RETRY_LEASE_WATCHDOG.shutdownNow();
    }

    /** 首次执行 + 3 次指数退避重试。 */
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] RETRY_DELAYS_SECONDS = {10L, 30L, 60L};
    private static final DefaultRedisScript<Long> RELEASE_RETRY_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
                    Long.class
            );
    private static final DefaultRedisScript<Long> RENEW_RETRY_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('EXPIRE', KEYS[1], ARGV[2]); end; return 0;",
                    Long.class
            );
    private static final ScheduledExecutorService RETRY_LEASE_WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "message-persist-lease-watchdog");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static final DefaultRedisScript<Long> MARK_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1]); " +
                            "redis.call('EXPIRE', KEYS[1], ARGV[4]); " +
                            "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); " +
                            "return 1;",
                    Long.class
            );
    public static final String DEADLETTER_NOT_FOUND = "__NOT_FOUND__";
    public static final String DEADLETTER_PAYLOAD_EXPIRED = "__PAYLOAD_EXPIRED__";
    public static final DefaultRedisScript<String> REPLAY_DEADLETTER_SCRIPT =
            new DefaultRedisScript<>(
                    "if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then "
                            + "return '__NOT_FOUND__'; end; "
                            + "local payload = redis.call('GET', KEYS[3]); "
                            + "if not payload then return '__PAYLOAD_EXPIRED__'; end; "
                            + "redis.call('ZREM', KEYS[1], ARGV[1]); "
                            + "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1]); "
                            + "return payload;",
                    String.class
            );

    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor messagePersistExecutor;
    private final ChatMonitoringMetrics monitoringMetrics;

    public MessagePersistServiceImpl(
            ChatMessageMapper chatMessageMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("messagePersistExecutor")
            ThreadPoolTaskExecutor messagePersistExecutor,
            ChatMonitoringMetrics monitoringMetrics
    ) {
        this.chatMessageMapper = chatMessageMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.messagePersistExecutor = messagePersistExecutor;
        this.monitoringMetrics = monitoringMetrics;
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
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(TimeUnit.DAYS.toSeconds(
                            RedisConstants.PERSIST_PAYLOAD_TTL_DAYS
                    ))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("记录待落库消息失败", exception);
        }
    }

    @Override
    @Async("messagePersistExecutor")
    public void persistMessageAsync(ChatMessage message) {
        validateMessage(message);
        String leaseToken = tryAcquireRetryLease(message.getId());
        if (leaseToken == null) {
            return;
        }
        ScheduledFuture<?> renewal = startRetryLeaseRenewal(message.getId(), leaseToken);
        persistWithRetry(message, 1, leaseToken, renewal);
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
            String leaseToken = null;
            try {
                leaseToken = tryAcquireRetryLease(messageId);
                if (leaseToken == null) {
                    continue;
                }
                String payload = redisTemplate.opsForValue().get(
                        RedisConstants.PERSIST_PENDING_PAYLOAD + messageId
                );
                if (payload == null || payload.isBlank()) {
                    moveToDeadLetter(messageId);
                    releaseRetryLease(messageId, leaseToken);
                    continue;
                }
                ChatMessage message = objectMapper.readValue(payload, ChatMessage.class);
                String retryLeaseToken = leaseToken;
                messagePersistExecutor.execute(() -> {
                    ScheduledFuture<?> renewal = startRetryLeaseRenewal(message.getId(), retryLeaseToken);
                    persistWithRetry(message, 1, retryLeaseToken, renewal);
                });
            } catch (Exception exception) {
                releaseRetryLease(messageId, leaseToken);
                log.error("重新提交待落库消息失败，messageId={}", messageId, exception);
            }
        }
    }

    @Override
    public PageResult<DeadLetterMessageVO> findDeadLetters(
            long pageNo,
            long pageSize
    ) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));
        long start = (normalizedPageNo - 1L) * normalizedPageSize;
        long end = start + normalizedPageSize - 1L;
        Long totalValue = redisTemplate.opsForZSet().zCard(
                RedisConstants.PERSIST_DEADLETTER
        );
        long total = totalValue == null ? 0L : totalValue;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(
                        RedisConstants.PERSIST_DEADLETTER,
                        start,
                        end
                );
        List<DeadLetterMessageVO> records = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String messageId = tuple.getValue();
                Double failedAtMillis = tuple.getScore();
                if (messageId == null || failedAtMillis == null) {
                    continue;
                }
                LocalDateTime failedAt = Instant.ofEpochMilli(
                                failedAtMillis.longValue()
                        )
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                String payload = redisTemplate.opsForValue().get(
                        RedisConstants.PERSIST_PENDING_PAYLOAD + messageId
                );
                boolean payloadAvailable = payload != null && !payload.isBlank();
                ChatMessage summary = parseDeadLetterSummary(messageId, payload);
                records.add(new DeadLetterMessageVO(
                        messageId,
                        failedAt,
                        payloadAvailable,
                        summary == null ? null : summary.getSessionId(),
                        summary == null ? null : summary.getSenderId(),
                        summary == null ? null : summary.getType()
                ));
            }
        }
        long pages = total == 0L
                ? 0L
                : (total + normalizedPageSize - 1L) / normalizedPageSize;
        return new PageResult<>(
                normalizedPageNo,
                normalizedPageSize,
                total,
                pages,
                records
        );
    }

    @Override
    public void replayDeadLetter(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId不能为空");
        }
        try {
            String payload = redisTemplate.execute(
                    REPLAY_DEADLETTER_SCRIPT,
                    List.of(
                            RedisConstants.PERSIST_DEADLETTER,
                            RedisConstants.PERSIST_PENDING,
                            RedisConstants.PERSIST_PENDING_PAYLOAD + messageId
                    ),
                    messageId,
                    String.valueOf(System.currentTimeMillis())
            );
            if (DEADLETTER_NOT_FOUND.equals(payload)) {
                throw new IllegalArgumentException("死信消息不存在");
            }
            if (DEADLETTER_PAYLOAD_EXPIRED.equals(payload)) {
                throw new BusinessStateException("死信消息内容已过期，无法重放");
            }
            if (payload == null || payload.isBlank()) {
                throw new IllegalStateException("死信重放脚本没有返回消息内容");
            }
            ChatMessage message = objectMapper.readValue(payload, ChatMessage.class);
            messagePersistExecutor.execute(() -> {
                String leaseToken = tryAcquireRetryLease(messageId);
                if (leaseToken == null) {
                    return;
                }
                ScheduledFuture<?> renewal = startRetryLeaseRenewal(messageId, leaseToken);
                persistWithRetry(message, 1, leaseToken, renewal);
            });
        } catch (BusinessStateException | IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("死信消息内容解析失败", exception);
        }
    }

    @Override
    public void deleteDeadLetter(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId不能为空");
        }
        Long removed = redisTemplate.opsForZSet().remove(RedisConstants.PERSIST_DEADLETTER, messageId);
        redisTemplate.delete(RedisConstants.PERSIST_PENDING_PAYLOAD + messageId);
        if (removed == null || removed == 0L) {
            throw new IllegalArgumentException("死信消息不存在");
        }
    }

    private ChatMessage parseDeadLetterSummary(String messageId, String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, ChatMessage.class);
        } catch (Exception exception) {
            log.warn("死信消息摘要解析失败，messageId={}", messageId);
            return null;
        }
    }

    @Override
    public int cleanupExpiredDeadLetters(long cutoffEpochMillis, int batchSize) {
        Set<String> expiredIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.PERSIST_DEADLETTER,
                0,
                cutoffEpochMillis,
                0,
                Math.max(1, batchSize)
        );
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        redisTemplate.opsForZSet().remove(
                RedisConstants.PERSIST_DEADLETTER,
                expiredIds.toArray()
        );
        List<String> payloadKeys = expiredIds.stream()
                .map(id -> RedisConstants.PERSIST_PENDING_PAYLOAD + id)
                .toList();
        redisTemplate.delete(payloadKeys);
        return expiredIds.size();
    }

    private void persistWithRetry(
            ChatMessage message,
            int attempt,
            String leaseToken,
            ScheduledFuture<?> renewal
    ) {
        try {
            int insertedRows = chatMessageMapper.insert(message);
            if (insertedRows != 1) {
                throw new IllegalStateException("消息写入行数不是 1");
            }
            markStored(message);
            completeRetry(message.getId(), leaseToken, renewal);
        } catch (DuplicateKeyException exception) {
            ChatMessage storedMessage = chatMessageMapper.findByClientMessage(
                    message.getSessionId(), message.getSenderId(), message.getClientMsgId()
            );
            if (!isSameLogicalMessage(message, storedMessage)) {
                log.error("消息唯一键冲突且内容不匹配，messageId={}", message.getId(), exception);
                moveToDeadLetter(message.getId());
            } else {
                markStored(storedMessage);
            }
            completeRetry(message.getId(), leaseToken, renewal);
        } catch (Exception exception) {
            log.warn("消息落库失败，第 {} 次尝试，messageId={}", attempt, message.getId(), exception);
            if (attempt == MAX_ATTEMPTS) {
                moveToDeadLetter(message.getId());
                completeRetry(message.getId(), leaseToken, renewal);
                return;
            }
            RETRY_LEASE_WATCHDOG.schedule(() -> {
                try {
                    messagePersistExecutor.execute(
                            () -> persistWithRetry(message, attempt + 1, leaseToken, renewal)
                    );
                } catch (RuntimeException submissionFailure) {
                    completeRetry(message.getId(), leaseToken, renewal);
                    log.error("延迟重试消息提交失败，messageId={}", message.getId(), submissionFailure);
                }
            },
                    RETRY_DELAYS_SECONDS[attempt - 1],
                    TimeUnit.SECONDS
            );
        }
    }

    private void completeRetry(
            String messageId,
            String leaseToken,
            ScheduledFuture<?> renewal
    ) {
        stopRetryLeaseRenewal(renewal);
        releaseRetryLease(messageId, leaseToken);
    }

    private void markStored(ChatMessage message) {
        String messageId = message.getId();
        Double pendingSince = redisTemplate.opsForZSet().score(
                RedisConstants.PERSIST_PENDING,
                messageId
        );
        redisTemplate.opsForZSet().remove(RedisConstants.PERSIST_PENDING, messageId);
        redisTemplate.delete(RedisConstants.PERSIST_PENDING_PAYLOAD + messageId);
        if (pendingSince != null) {
            monitoringMetrics.recordMessagePersisted(
                    java.time.Duration.ofMillis(
                            Math.max(0L, System.currentTimeMillis() - pendingSince.longValue())
                    )
            );
        }
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
        redisTemplate.expire(
                RedisConstants.PERSIST_PENDING_PAYLOAD + messageId,
                RedisConstants.PERSIST_DEADLETTER_RETENTION_DAYS,
                TimeUnit.DAYS
        );
        log.error("消息达到最大落库重试次数，已转入死信集合，messageId={}", messageId);
    }

    private void validateMessage(ChatMessage message) {
        if (message == null || message.getId() == null || message.getId().isBlank()) {
            throw new IllegalArgumentException("消息或消息 ID 不能为空");
        }
    }

    private boolean isSameLogicalMessage(ChatMessage expected, ChatMessage stored) {
        return stored != null
                && expected.getSessionId().equals(stored.getSessionId())
                && expected.getSenderId().equals(stored.getSenderId())
                && expected.getClientMsgId().equals(stored.getClientMsgId())
                && expected.getType().equals(stored.getType())
                && expected.getContent().equals(stored.getContent());
    }

    private void releaseRetryLease(String messageId, String token) {
        if (messageId != null && !messageId.isBlank() && token != null && !token.isBlank()) {
            redisTemplate.execute(
                    RELEASE_RETRY_LEASE_SCRIPT,
                    List.of(RedisConstants.PERSIST_RETRY_LEASE + messageId),
                    token
            );
        }
    }

    private String tryAcquireRetryLease(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                RedisConstants.PERSIST_RETRY_LEASE + messageId,
                token,
                RedisConstants.PERSIST_RETRY_LEASE_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private ScheduledFuture<?> startRetryLeaseRenewal(String messageId, String token) {
        long interval = Math.max(1L, RedisConstants.PERSIST_RETRY_LEASE_SECONDS / 3L);
        return RETRY_LEASE_WATCHDOG.scheduleAtFixedRate(() -> {
            try {
                Long renewed = redisTemplate.execute(
                        RENEW_RETRY_LEASE_SCRIPT,
                        List.of(RedisConstants.PERSIST_RETRY_LEASE + messageId),
                        token,
                        String.valueOf(RedisConstants.PERSIST_RETRY_LEASE_SECONDS)
                );
                if (!Long.valueOf(1L).equals(renewed)) {
                    log.warn("消息落库 lease 已失效，messageId={}", messageId);
                }
            } catch (RuntimeException exception) {
                log.warn("消息落库 lease 续期失败，messageId={}", messageId, exception);
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void stopRetryLeaseRenewal(ScheduledFuture<?> renewal) {
        if (renewal != null) {
            renewal.cancel(false);
        }
    }
}
