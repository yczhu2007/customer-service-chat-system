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
    private final IChatService chatService;

    public QueueTimeoutSweeper(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            IChatService chatService,
            @Value("${app.chat.queue.timeout-seconds:300}") long timeoutSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.timeoutMillis = timeoutSeconds * 1000L;
        this.chatService = chatService;
    }

    @Scheduled(fixedDelayString = "${app.chat.queue.sweep-delay-ms:15000}")
    public void removeTimedOutUsers() {
        migrateLegacySequenceScores();
        long deadline = System.currentTimeMillis() - timeoutMillis;
        Set<String> userIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.QUEUE_PENDING,
                0,
                deadline,
                0,
                100
        );
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            if (Boolean.TRUE.equals(redisTemplate.opsForZSet().remove(
                    RedisConstants.QUEUE_PENDING,
                    userId
            ))) {
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/chat",
                        java.util.Map.of(
                                "event", "WAITING_TIMEOUT",
                                "message", "排队超时，请稍后重新发起咨询"
                        )
                );
            }
        }
        chatService.refreshWaitingPositions();
    }

    /**
     * 兼容修复前以纯递增序号作为 score 的排队数据，避免升级后被立即判定超时。
     */
    private void migrateLegacySequenceScores() {
        Set<String> legacyUserIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.QUEUE_PENDING,
                0,
                999_999_999_999D,
                0,
                100
        );
        if (legacyUserIds == null || legacyUserIds.isEmpty()) {
            return;
        }
        for (String userId : legacyUserIds) {
            chatService.enqueueWaitingUser(userId);
        }
    }
}
