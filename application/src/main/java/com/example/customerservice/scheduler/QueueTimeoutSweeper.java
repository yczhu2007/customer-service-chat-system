package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.IChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 清理等待超时用户，避免无限排队。 */
@Component
public class QueueTimeoutSweeper {

    private static final DefaultRedisScript<Long> REMOVE_TIMED_OUT_USER_SCRIPT =
            new DefaultRedisScript<>(
                    "local enqueuedAt = redis.call('ZSCORE', KEYS[2], ARGV[1]); " +
                            "if not enqueuedAt then return 0; end; " +
                            "local vipLevel = tonumber(redis.call('HGET', KEYS[3], ARGV[1]) or '0'); " +
                            "local timeout = tonumber(ARGV[3]); " +
                            "if vipLevel > 0 then timeout = tonumber(ARGV[4]); end; " +
                            "if tonumber(enqueuedAt) > tonumber(ARGV[2]) - timeout then return 0; end; " +
                            "if redis.call('ZREM', KEYS[1], ARGV[1]) == 0 then return 0; end; " +
                            "redis.call('ZREM', KEYS[2], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[3], ARGV[1]); " +
                            "redis.call('ZREM', KEYS[4], ARGV[1]); " +
                            "return vipLevel + 1;",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final long timeoutMillis;
    private final long vipTimeoutMillis;
    private final long vipPriorityStepMillis;
    private final IChatService chatService;

    public QueueTimeoutSweeper(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            IChatService chatService,
            @Value("${app.chat.queue.timeout-seconds:300}") long timeoutSeconds,
            @Value("${app.chat.queue.vip-timeout-seconds:120}") long vipTimeoutSeconds,
            @Value("${app.chat.queue.vip-priority-step-seconds:30}")
            long vipPriorityStepSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.timeoutMillis = timeoutSeconds * 1000L;
        this.vipTimeoutMillis = vipTimeoutSeconds * 1000L;
        this.vipPriorityStepMillis = Math.max(
                0L,
                vipPriorityStepSeconds * 1000L
        );
        this.chatService = chatService;
    }

    @Scheduled(fixedDelayString = "${app.chat.queue.sweep-delay-ms:15000}")
    public void removeTimedOutUsers() {
        backfillMissingEnqueueTimes();
        normalizeFairPriorityScores();
        long now = System.currentTimeMillis();
        long earliestDeadline =
                now - Math.min(timeoutMillis, vipTimeoutMillis);
        Set<String> userIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.QUEUE_ENQUEUED_AT,
                0,
                earliestDeadline,
                0,
                100
        );
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            Long removedVipMarker = redisTemplate.execute(
                    REMOVE_TIMED_OUT_USER_SCRIPT,
                    java.util.List.of(
                            RedisConstants.QUEUE_PENDING,
                            RedisConstants.QUEUE_ENQUEUED_AT,
                            RedisConstants.QUEUE_VIP_LEVEL,
                            RedisConstants.VIP_CALLBACK_PENDING
                    ),
                    userId,
                    String.valueOf(now),
                    String.valueOf(timeoutMillis),
                    String.valueOf(vipTimeoutMillis)
            );
            if (removedVipMarker != null && removedVipMarker > 0) {
                int vipLevel = Math.toIntExact(removedVipMarker - 1);
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/chat",
                        java.util.Map.of(
                                "event", "WAITING_TIMEOUT",
                                "message", "排队超时，请稍后重新发起咨询"
                        )
                );
                if (vipLevel > 0) {
                    notifyAgentsAboutVipTimeout(
                            userId,
                            vipLevel
                    );
                }
            }
        }
        chatService.refreshWaitingPositions();
    }

    /**
     * 把历史版本使用的无限VIP偏移量迁移为有限优先窗口。
     * 普通用户到达时间足够早时，会自然排到后来到达的VIP用户之前。
     */
    private void normalizeFairPriorityScores() {
        Set<String> queuedUserIds = redisTemplate.opsForZSet().range(
                RedisConstants.QUEUE_PENDING,
                0,
                -1
        );
        if (queuedUserIds == null || queuedUserIds.isEmpty()) {
            return;
        }
        for (String userId : queuedUserIds) {
            Double enqueuedAt = redisTemplate.opsForZSet().score(
                    RedisConstants.QUEUE_ENQUEUED_AT,
                    userId
            );
            if (enqueuedAt == null) {
                continue;
            }
            Object vipLevelValue = redisTemplate.opsForHash().get(
                    RedisConstants.QUEUE_VIP_LEVEL,
                    userId
            );
            int vipLevel = parseVipLevel(vipLevelValue);
            double fairScore = enqueuedAt
                    - (double) vipLevel * vipPriorityStepMillis;
            redisTemplate.opsForZSet().add(
                    RedisConstants.QUEUE_PENDING,
                    userId,
                    fairScore
            );
        }
    }

    /** 为升级前的排队数据补齐独立的真实入队时间索引。 */
    private void backfillMissingEnqueueTimes() {
        Set<String> queuedUserIds = redisTemplate.opsForZSet().range(
                RedisConstants.QUEUE_PENDING,
                0,
                99
        );
        if (queuedUserIds == null || queuedUserIds.isEmpty()) {
            return;
        }
        for (String userId : queuedUserIds) {
            Double enqueuedAt = redisTemplate.opsForZSet().score(
                    RedisConstants.QUEUE_ENQUEUED_AT,
                    userId
            );
            if (enqueuedAt == null) {
                chatService.enqueueWaitingUser(userId);
            }
        }
    }

    private int parseVipLevel(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.toString()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void notifyAgentsAboutVipTimeout(
            String userId,
            int vipLevel
    ) {
        Set<String> onlineAgentIds =
                redisTemplate.opsForZSet().range(
                        RedisConstants.AGENT_LOAD,
                        0,
                        -1
                );
        if (onlineAgentIds == null || onlineAgentIds.isEmpty()) {
            return;
        }
        for (String agentId : onlineAgentIds) {
            messagingTemplate.convertAndSendToUser(
                    agentId,
                    "/queue/chat",
                    java.util.Map.of(
                            "event", "VIP_WAITING_TIMEOUT",
                            "userId", userId,
                            "vipLevel", vipLevel,
                            "message", "VIP用户等待超时，请优先处理"
                    )
            );
        }
    }
}
