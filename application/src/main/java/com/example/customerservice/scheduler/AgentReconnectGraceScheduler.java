package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatPresenceOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/** 宽限期结束后，才对未重连客服执行完整断线清理。 */
@Component
public class AgentReconnectGraceScheduler {

    private final ChatPresenceOperations chatPresenceOperations;
    private final DistributedSchedulerLock schedulerLock;

    public AgentReconnectGraceScheduler(
            ChatPresenceOperations chatPresenceOperations,
            DistributedSchedulerLock schedulerLock
    ) {
        this.chatPresenceOperations = chatPresenceOperations;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelay = 5_000)
    public void handleExpiredGracePeriods() {
        schedulerLock.execute(
                "agent-reconnect-grace",
                this::handleExpiredGracePeriodsLocked
        );
    }

    private void handleExpiredGracePeriodsLocked() {
        /* Set<String> agentIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.AGENT_RECONNECT_GRACE,
                0,
                System.currentTimeMillis(),
                0,
                100
        );
        if (agentIds == null || agentIds.isEmpty()) {
            return;
        }
        for (String agentId : agentIds) {
            chatPresenceOperations.handleAgentReconnectGraceTimeout(agentId);
        } */
        chatPresenceOperations.handleExpiredReconnectGracePeriods(System.currentTimeMillis(), 100);
    }
}
