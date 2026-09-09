package com.example.customerservice.service.impl;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.constant.RoleCodes;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.domain.ChatAgentSkill;
import com.example.customerservice.mapper.ChatAgentSkillMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ChatAgentSkillMapper chatAgentSkillMapper;

    public ChatAgentService(
            ChatRedisRepository chatRedisRepository,
            ChatAgentSessionRecoveryService sessionRecoveryService,
            ChatRoutingOperations chatRoutingOperations,
            ChatPresenceOperations chatPresenceOperations,
            ChatSessionNotificationOperations notificationOperations,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            ChatAgentSkillMapper chatAgentSkillMapper
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.sessionRecoveryService = sessionRecoveryService;
        this.chatRoutingOperations = chatRoutingOperations;
        this.chatPresenceOperations = chatPresenceOperations;
        this.notificationOperations = notificationOperations;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.chatAgentSkillMapper = chatAgentSkillMapper;
    }

    @Override
    public void agentOnline(String agentId) {
        requireAgentId(agentId);
        chatRedisRepository.sortedSetRemove(RedisConstants.AGENT_RECONNECT_GRACE, agentId);
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
    @Transactional
    public void setAgentVipSkill(String agentId, boolean enabled) {
        requireAgentId(agentId);
        SysUser agent = sysUserMapper.selectById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("客服不存在");
        }
        Set<String> roleCodes = sysUserRoleMapper.findRoleCodesByUserId(agentId);
        if (roleCodes == null || !roleCodes.contains(RoleCodes.AGENT)) {
            throw new IllegalArgumentException("该用户不具有AGENT角色");
        }
        if (enabled) {
            Long existingCount = chatAgentSkillMapper.selectCount(
                    Wrappers.<ChatAgentSkill>lambdaQuery()
                            .eq(ChatAgentSkill::getAgentId, agentId)
                            .eq(
                                    ChatAgentSkill::getSkillCode,
                                    ChatConstants.AGENT_SKILL_VIP_CODE
                            )
            );
            if (existingCount == null || existingCount == 0L) {
                ChatAgentSkill skill = new ChatAgentSkill();
                skill.setId(UUID.randomUUID().toString().replace("-", ""));
                skill.setAgentId(agentId);
                skill.setSkillCode(ChatConstants.AGENT_SKILL_VIP_CODE);
                chatAgentSkillMapper.insert(skill);
            }
        } else {
            chatAgentSkillMapper.delete(
                    Wrappers.<ChatAgentSkill>lambdaQuery()
                            .eq(ChatAgentSkill::getAgentId, agentId)
                            .eq(
                                    ChatAgentSkill::getSkillCode,
                                    ChatConstants.AGENT_SKILL_VIP_CODE
                            )
            );
        }
        refreshVipSkillCacheAfterCommit();
    }

    @Override
    public void setAgentVipSkillByLoginNumber(String loginNumber, boolean enabled) {
        SysUser agent = sysUserMapper.findByUsername(loginNumber == null ? null : loginNumber.trim());
        if (agent == null || !"ENABLED".equals(agent.getStatus())) {
            throw new IllegalArgumentException("客服登录编号不存在或账号已禁用");
        }
        Set<String> roles = sysUserRoleMapper.findRoleCodesByUserId(agent.getId());
        if (roles == null || !roles.contains(RoleCodes.AGENT)) {
            throw new IllegalArgumentException("该登录编号不是客服账号");
        }
        setAgentVipSkill(agent.getId(), enabled);
    }

    @Override
    public Set<String> findVipSkillAgentIds() {
        Set<String> agentIds = chatRedisRepository.setMembers(
                RedisConstants.AGENT_SKILL_VIP
        );
        if (agentIds != null && !agentIds.isEmpty()) {
            return Set.copyOf(agentIds);
        }
        List<String> persistedAgentIds = chatAgentSkillMapper.selectList(
                        Wrappers.<ChatAgentSkill>lambdaQuery()
                                .eq(
                                        ChatAgentSkill::getSkillCode,
                                        ChatConstants.AGENT_SKILL_VIP_CODE
                                )
                )
                .stream()
                .map(ChatAgentSkill::getAgentId)
                .distinct()
                .toList();
        if (!persistedAgentIds.isEmpty()) {
            chatRedisRepository.setAdd(
                    RedisConstants.AGENT_SKILL_VIP,
                    persistedAgentIds.toArray(String[]::new)
            );
        }
        return Set.copyOf(persistedAgentIds);
    }

    @Override
    public Set<String> findVipSkillAgentLoginNumbers() {
        return findVipSkillAgentIds().stream()
                .map(sysUserMapper::selectById)
                .filter(user -> user != null && user.getUsername() != null && !user.getUsername().isBlank())
                .map(SysUser::getUsername)
                .collect(Collectors.toSet());
    }

    private void refreshVipSkillCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            rebuildVipSkillCacheSafely();
                        }
                    }
            );
            return;
        }
        rebuildVipSkillCacheSafely();
    }

    private void rebuildVipSkillCacheSafely() {
        try {
            List<String> persistedAgentIds = chatAgentSkillMapper.selectList(
                            Wrappers.<ChatAgentSkill>lambdaQuery()
                                    .eq(
                                            ChatAgentSkill::getSkillCode,
                                            ChatConstants.AGENT_SKILL_VIP_CODE
                                    )
                    )
                    .stream()
                    .map(ChatAgentSkill::getAgentId)
                    .distinct()
                    .toList();
            chatRedisRepository.delete(RedisConstants.AGENT_SKILL_VIP);
            if (!persistedAgentIds.isEmpty()) {
                chatRedisRepository.setAdd(
                        RedisConstants.AGENT_SKILL_VIP,
                        persistedAgentIds.toArray(String[]::new)
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "数据库已更新，但VIP客服技能缓存重建失败；后续空缓存回源或应用重启将继续修复",
                    exception
            );
        }
    }

    private void fillAvailableCapacity(String agentId) {
        try {
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
        } finally {
            chatRoutingOperations.refreshWaitingPositions();
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
