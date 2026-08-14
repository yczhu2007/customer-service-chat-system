package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.ChatMaintenanceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 将长时间没有新消息的活动会话转分配给其他可用客服。 */
@Component
@Slf4j
public class SessionInactivityScheduler {

    private final StringRedisTemplate redisTemplate;
    private final ChatMaintenanceOperations chatMaintenanceOperations;
    private final long inactivityTimeoutMillis;
    private final DistributedSchedulerLock schedulerLock;

    public SessionInactivityScheduler(
            StringRedisTemplate redisTemplate,
            ChatMaintenanceOperations chatMaintenanceOperations,
            DistributedSchedulerLock schedulerLock,
            @Value("${app.chat.session.inactivity-timeout-seconds:1800}")
            long inactivityTimeoutSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.chatMaintenanceOperations = chatMaintenanceOperations;
        this.schedulerLock = schedulerLock;
        this.inactivityTimeoutMillis = TimeUnit.SECONDS.toMillis(
                Math.max(60L, inactivityTimeoutSeconds)
        );
    }

    @Scheduled(
            fixedDelayString = "${app.chat.session.inactivity-sweep-delay-ms:30000}"
    )
    public void reassignInactiveSessions() {
        schedulerLock.execute(
                "session-inactivity",
                this::reassignInactiveSessionsLocked
        );
    }

    private void reassignInactiveSessionsLocked() {
        long cutoffMillis = System.currentTimeMillis() - inactivityTimeoutMillis;
        Set<String> sessionIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.SESSION_LAST_ACTIVITY,
                0,
                cutoffMillis,
                0,
                100
        );
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        for (String sessionId : sessionIds) {
            try {
                chatMaintenanceOperations.handleSessionInactivityTimeout(
                        sessionId,
                        cutoffMillis
                );
            } catch (Exception exception) {
                log.error(
                        "会话无活动超时转分配失败，sessionId={}",
                        sessionId,
                        exception
                );
            }
        }
    }
}
