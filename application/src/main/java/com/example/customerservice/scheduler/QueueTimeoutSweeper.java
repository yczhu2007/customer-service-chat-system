package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.IChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 清理等待超时用户，避免无限排队。 */
@Component
public class QueueTimeoutSweeper {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final long timeoutMillis;
    private final long vipTimeoutMillis;
    private final IChatService chatService;

    public QueueTimeoutSweeper(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            IChatService chatService,
            @Value("${app.chat.queue.timeout-seconds:300}") long timeoutSeconds,
            @Value("${app.chat.queue.vip-timeout-seconds:120}") long vipTimeoutSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.timeoutMillis = timeoutSeconds * 1000L;
        this.vipTimeoutMillis = vipTimeoutSeconds * 1000L;
        this.chatService = chatService;
    }

    @Scheduled(fixedDelayString = "${app.chat.queue.sweep-delay-ms:15000}")
    public void removeTimedOutUsers() {
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
        for (String userId : userIds) {
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
            long applicableTimeout =
                    vipLevel > 0
                            ? vipTimeoutMillis
                            : timeoutMillis;
            if (enqueuedAt > now - applicableTimeout) {
                continue;
            }
            boolean removed = Boolean.TRUE.equals(
                    redisTemplate.opsForZSet().remove(
                            RedisConstants.QUEUE_PENDING,
                            userId
                    )
                );
            redisTemplate.opsForZSet().remove(
                    RedisConstants.QUEUE_ENQUEUED_AT,
                    userId
            );
            redisTemplate.opsForHash().delete(
                    RedisConstants.QUEUE_VIP_LEVEL,
                    userId
            );
            redisTemplate.opsForZSet().remove(
                    RedisConstants.VIP_CALLBACK_PENDING,
                    userId
            );
            if (removed) {
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
