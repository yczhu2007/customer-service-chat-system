package com.example.customerservice.service.impl;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/** 客服可用状态、上线恢复、接待容量和技能组领域服务。 */
@Slf4j
public class ChatAgentService implements ChatAgentOperations {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatAgentSessionRecoveryService sessionRecoveryService;
    private final ChatRoutingOperations chatRoutingOperations;
    private final ChatPresenceOperations chatPresenceOperations;
    private final ChatSessionNotificationOperations notificationOperations;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public ChatAgentService(
            ChatRedisRepository chatRedisRepository,
            ChatAgentSessionRecoveryService sessionRecoveryService,
            ChatRoutingOperations chatRoutingOperations,
            ChatPresenceOperations chatPresenceOperations,
            ChatSessionNotificationOperations notificationOperations,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.sessionRecoveryService = sessionRecoveryService;
        this.chatRoutingOperations = chatRoutingOperations;
        this.chatPresenceOperations = chatPresenceOperations;
        this.notificationOperations = notificationOperations;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public void agentOnline(String agentId) {
        requireAgentId(agentId);
        List<ChatSession> activeSessions =
                sessionRecoveryService.restoreActiveSessions(agentId);

        chatRedisRepository.sortedSetAdd(
                RedisConstants.AGENT_LOAD,
                agentId,
                activeSessions.size()
        );
        if (chatRedisRepository.sortedSetScore(
                RedisConstants.AGENT_LAST_ASSIGNED,
                agentId
        ) == null) {
            chatRedisRepository.sortedSetAdd(
                    RedisConstants.AGENT_LAST_ASSIGNED,
                    agentId,
                    System.currentTimeMillis()
            );
        }

        activeSessions.forEach(session -> notificationOperations.notifyBothParties(
                session,
                resolveVipLevel(session.getUserId())
        ));

        log.info(
                "客服上线，agentId={}，已恢复活动会话数={}",
                agentId,
                activeSessions.size()
        );
        fillAvailableCapacity(agentId);
    }

    @Override
    public void agentOffline(String agentId) {
        requireAgentId(agentId);
        chatRedisRepository.sortedSetRemove(RedisConstants.AGENT_LOAD, agentId);
        chatRedisRepository.sortedSetRemove(
                RedisConstants.AGENT_LAST_ASSIGNED,
                agentId
        );
        chatRedisRepository.sortedSetRemove(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId
        );
        chatPresenceOperations.disconnectAgent(
                agentId,
                ChatConstants.REASON_AGENT_OFFLINE
        );
    }

    @Override
    public void setAgentVipSkill(String agentId, boolean enabled) {
        requireAgentId(agentId);
        SysUser agent = sysUserMapper.selectById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("客服不存在");
        }
        Set<String> roleCodes = sysUserRoleMapper.findRoleCodesByUserId(agentId);
        if (roleCodes == null || !roleCodes.contains("AGENT")) {
            throw new IllegalArgumentException("该用户不具有AGENT角色");
        }
        if (enabled) {
            chatRedisRepository.setAdd(RedisConstants.AGENT_SKILL_VIP, agentId);
        } else {
            chatRedisRepository.setRemove(RedisConstants.AGENT_SKILL_VIP, agentId);
        }
    }

    @Override
    public Set<String> findVipSkillAgentIds() {
        Set<String> agentIds = chatRedisRepository.setMembers(
                RedisConstants.AGENT_SKILL_VIP
        );
        return agentIds == null ? Set.of() : agentIds;
    }

    private void fillAvailableCapacity(String agentId) {
        for (int slot = 0; slot < RedisConstants.AGENT_MAX_CONCURRENCY; slot++) {
            Long waitingCount = chatRedisRepository.sortedSetCardinality(
                    RedisConstants.QUEUE_PENDING
            );
            Double currentLoad = chatRedisRepository.sortedSetScore(
                    RedisConstants.AGENT_LOAD,
                    agentId
            );
            if (waitingCount == null
                    || waitingCount <= 0
                    || currentLoad == null
                    || currentLoad >= RedisConstants.AGENT_MAX_CONCURRENCY) {
                return;
            }
            chatRoutingOperations.processNextWaitingUser(agentId);
        }
    }

    private int resolveVipLevel(String userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getVipLevel() == null) {
            return 0;
        }
        return Math.max(0, Math.min(user.getVipLevel(), 5));
    }

    private void requireAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId不能为空");
        }
    }
}
