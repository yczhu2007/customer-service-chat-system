package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatAgentSkill;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.ChatAgentSkillMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatAgentService;
import com.example.customerservice.service.impl.ChatAgentSessionRecoveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAgentServiceTest {

    @Test
    void enablingVipSkillPersistsDatabaseBeforeUpdatingRedisCache() {
        ChatRedisRepository redisRepository = mock(ChatRedisRepository.class);
        ChatAgentSessionRecoveryService recoveryService =
                mock(ChatAgentSessionRecoveryService.class);
        ChatRoutingOperations routingOperations = mock(ChatRoutingOperations.class);
        ChatPresenceOperations presenceOperations = mock(ChatPresenceOperations.class);
        ChatSessionNotificationOperations notificationOperations =
                mock(ChatSessionNotificationOperations.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        ChatAgentSkillMapper skillMapper = mock(ChatAgentSkillMapper.class);
        SysUser agent = new SysUser();
        agent.setId("A001");
        when(userMapper.selectById("A001")).thenReturn(agent);
        when(userRoleMapper.findRoleCodesByUserId("A001"))
                .thenReturn(Set.of("AGENT"));
        when(skillMapper.selectCount(any())).thenReturn(0L);
        ChatAgentSkill persistedSkill = new ChatAgentSkill();
        persistedSkill.setAgentId("A001");
        persistedSkill.setSkillCode("VIP");
        when(skillMapper.selectList(any())).thenReturn(List.of(persistedSkill));
        ChatAgentService service = new ChatAgentService(
                redisRepository,
                recoveryService,
                routingOperations,
                presenceOperations,
                notificationOperations,
                userMapper,
                userRoleMapper,
                skillMapper
        );

        service.setAgentVipSkill("A001", true);

        ArgumentCaptor<ChatAgentSkill> skillCaptor =
                ArgumentCaptor.forClass(ChatAgentSkill.class);
        verify(skillMapper).insert(skillCaptor.capture());
        assertEquals("A001", skillCaptor.getValue().getAgentId());
        assertEquals("VIP", skillCaptor.getValue().getSkillCode());
        verify(redisRepository).delete(RedisConstants.AGENT_SKILL_VIP);
        verify(redisRepository).setAdd(RedisConstants.AGENT_SKILL_VIP, "A001");
    }

    @Test
    void agentOnlineCancelsPendingReconnectGrace() {
        ChatRedisRepository redisRepository = mock(ChatRedisRepository.class);
        ChatAgentSessionRecoveryService recoveryService = mock(ChatAgentSessionRecoveryService.class);
        ChatRoutingOperations routingOperations = mock(ChatRoutingOperations.class);
        ChatPresenceOperations presenceOperations = mock(ChatPresenceOperations.class);
        ChatSessionNotificationOperations notificationOperations = mock(ChatSessionNotificationOperations.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        ChatAgentSkillMapper skillMapper = mock(ChatAgentSkillMapper.class);
        SysUser agent = new SysUser();
        agent.setId("A001");
        when(userMapper.selectById("A001")).thenReturn(agent);
        when(userRoleMapper.findRoleCodesByUserId("A001")).thenReturn(Set.of("AGENT"));
        when(recoveryService.restoreActiveSessions("A001")).thenReturn(List.<ChatSession>of());

        ChatAgentService service = new ChatAgentService(
                redisRepository, recoveryService, routingOperations, presenceOperations,
                notificationOperations, userMapper, userRoleMapper, skillMapper
        );

        service.agentOnline("A001");

        verify(redisRepository).sortedSetRemove(RedisConstants.AGENT_RECONNECT_GRACE, "A001");
    }

    @Test
    void agentOnlineRefreshesWaitingPositionsOnceAfterCapacityCheck() {
        ChatRedisRepository redisRepository = mock(ChatRedisRepository.class);
        ChatAgentSessionRecoveryService recoveryService = mock(ChatAgentSessionRecoveryService.class);
        ChatRoutingOperations routingOperations = mock(ChatRoutingOperations.class);
        ChatPresenceOperations presenceOperations = mock(ChatPresenceOperations.class);
        ChatSessionNotificationOperations notificationOperations = mock(ChatSessionNotificationOperations.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        ChatAgentSkillMapper skillMapper = mock(ChatAgentSkillMapper.class);
        when(recoveryService.restoreActiveSessions("A001")).thenReturn(List.of());
        when(redisRepository.sortedSetCardinality(RedisConstants.QUEUE_PENDING)).thenReturn(0L);
        ChatAgentService service = new ChatAgentService(
                redisRepository, recoveryService, routingOperations, presenceOperations,
                notificationOperations, userMapper, userRoleMapper, skillMapper
        );

        service.agentOnline("A001");

        verify(routingOperations).refreshWaitingPositions();
    }
}
