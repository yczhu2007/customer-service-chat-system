package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.IChatService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 宽限期结束后，才对未重连客服执行完整断线清理。 */
@Component
public class AgentReconnectGraceScheduler {

    private final StringRedisTemplate redisTemplate;
    private final IChatService chatService;

    public AgentReconnectGraceScheduler(
            StringRedisTemplate redisTemplate,
            IChatService chatService
    ) {
        this.redisTemplate = redisTemplate;
        this.chatService = chatService;
    }

    @Scheduled(fixedDelay = 5_000)
    public void handleExpiredGracePeriods() {
        Set<String> agentIds = redisTemplate.opsForZSet().rangeByScore(
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
            chatService.handleAgentReconnectGraceTimeout(agentId);
        }
    }
}
