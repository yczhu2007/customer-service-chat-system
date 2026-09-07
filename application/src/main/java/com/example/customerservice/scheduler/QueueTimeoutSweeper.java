package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.ChatRoutingOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 清理等待超时用户，避免无限排队。 */
@Component
@Slf4j
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
                            "redis.call('ZREM', KEYS[5], ARGV[1]); " +
                            "return vipLevel + 1;",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final long timeoutMillis;
    private final long vipTimeoutMillis;
    private final ChatRoutingOperations chatRoutingOperations;
    private final DistributedSchedulerLock schedulerLock;

    public QueueTimeoutSweeper(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            ChatRoutingOperations chatRoutingOperations,
            DistributedSchedulerLock schedulerLock,
            @Value("${app.chat.queue.timeout-seconds:300}") long timeoutSeconds,
            @Value("${app.chat.queue.vip-timeout-seconds:120}") long vipTimeoutSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.timeoutMillis = timeoutSeconds * 1000L;
        this.vipTimeoutMillis = vipTimeoutSeconds * 1000L;
        this.chatRoutingOperations = chatRoutingOperations;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.chat.queue.sweep-delay-ms:15000}")
    public void removeTimedOutUsers() {
        schedulerLock.execute(
                "queue-timeout",
                this::removeTimedOutUsersLocked
        );
    }

    private void removeTimedOutUsersLocked() {
        backfillMissingEnqueueTimes();
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
        Set<String> onlineAgentIds = null;
        for (String userId : userIds) {
            Long removedVipMarker = redisTemplate.execute(
                    REMOVE_TIMED_OUT_USER_SCRIPT,
                    java.util.List.of(
                            RedisConstants.QUEUE_PENDING,
                            RedisConstants.QUEUE_ENQUEUED_AT,
                            RedisConstants.QUEUE_VIP_LEVEL,
                            RedisConstants.QUEUE_NORMAL_DUE,
                            RedisConstants.VIP_CALLBACK_PENDING
                    ),
                    userId,
                    String.valueOf(now),
                    String.valueOf(timeoutMillis),
                    String.valueOf(vipTimeoutMillis)
            );
            if (removedVipMarker != null && removedVipMarker > 0) {
                int vipLevel = Math.toIntExact(removedVipMarker - 1);
                try {
                    messagingTemplate.convertAndSendToUser(
                            userId,
                            "/queue/chat",
                            java.util.Map.of(
                                    "event", "WAITING_TIMEOUT",
                                    "message", "排队超时，请稍后重新发起咨询"
                            )
                    );
                } catch (RuntimeException exception) {
                    log.warn("发送排队超时通知失败，userId={}", userId, exception);
                }
                if (vipLevel > 0) {
                    if (onlineAgentIds == null) {
                        onlineAgentIds = redisTemplate.opsForZSet().range(
                                RedisConstants.AGENT_LOAD,
                                0,
                                -1
                        );
                    }
                    notifyAgentsAboutVipTimeout(
                            userId,
                            vipLevel,
                            onlineAgentIds
                    );
                }
            }
        }
        chatRoutingOperations.refreshWaitingPositions();
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
        java.util.List<String> missingUserIds = new java.util.ArrayList<>();
        for (String userId : queuedUserIds) {
            Double enqueuedAt = redisTemplate.opsForZSet().score(
                    RedisConstants.QUEUE_ENQUEUED_AT,
                    userId
            );
            if (enqueuedAt == null) {
                missingUserIds.add(userId);
            }
        }
        if (!missingUserIds.isEmpty()) {
            chatRoutingOperations.backfillWaitingUsers(missingUserIds);
        }
    }

    private void notifyAgentsAboutVipTimeout(
            String userId,
            int vipLevel,
            Set<String> onlineAgentIds
    ) {
        if (onlineAgentIds == null || onlineAgentIds.isEmpty()) {
            return;
        }
        for (String agentId : onlineAgentIds) {
            try {
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
            } catch (RuntimeException exception) {
                log.warn("发送VIP排队超时通知失败，agentId={}", agentId, exception);
            }
        }
    }
}
