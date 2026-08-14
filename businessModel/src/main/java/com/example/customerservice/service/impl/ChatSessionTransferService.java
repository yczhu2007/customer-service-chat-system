package com.example.customerservice.service.impl;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.ChatSessionDTO;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatSessionTransferOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

@Slf4j
public class ChatSessionTransferService implements ChatSessionTransferOperations {

    private static final DefaultRedisScript<Long> TRANSFER_SESSION_REDIS_SCRIPT =
            new DefaultRedisScript<>(
                    "local targetLoad = redis.call('ZSCORE', KEYS[3], ARGV[3]); "
                            + "if not targetLoad or tonumber(targetLoad) >= tonumber(ARGV[4]) then return 0; end; "
                            + "local sourceLoad = redis.call('ZSCORE', KEYS[3], ARGV[2]); "
                            + "if not sourceLoad then return 0; end; "
                            + "redis.call('SREM', KEYS[1], ARGV[1]); "
                            + "redis.call('SADD', KEYS[2], ARGV[1]); "
                            + "redis.call('ZADD', KEYS[3], math.max(0, tonumber(sourceLoad) - 1), ARGV[2]); "
                            + "redis.call('ZINCRBY', KEYS[3], 1, ARGV[3]); "
                            + "redis.call('ZADD', KEYS[6], ARGV[5], ARGV[3]); "
                            + "redis.call('SET', KEYS[4], ARGV[3]); "
                            + "redis.call('HSET', KEYS[5], 'agentId', ARGV[3]); "
                            + "return 1;",
                    Long.class
            );

    private static final DefaultRedisScript<Long> ROLLBACK_TRANSFER_SESSION_REDIS_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SREM', KEYS[1], ARGV[1]); "
                            + "redis.call('SADD', KEYS[2], ARGV[1]); "
                            + "local targetLoad = redis.call('ZSCORE', KEYS[3], ARGV[2]); "
                            + "if targetLoad then redis.call('ZADD', KEYS[3], math.max(0, tonumber(targetLoad) - 1), ARGV[2]); end; "
                            + "redis.call('ZINCRBY', KEYS[3], 1, ARGV[3]); "
                            + "redis.call('SET', KEYS[4], ARGV[3]); "
                            + "redis.call('HSET', KEYS[5], 'agentId', ARGV[3]); "
                            + "return 1;",
                    Long.class
            );

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int agentMaxConcurrency;

    public ChatSessionTransferService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SimpMessagingTemplate messagingTemplate,
            TransactionTemplate transactionTemplate,
            int agentMaxConcurrency
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.messagingTemplate = messagingTemplate;
        this.transactionTemplate = transactionTemplate;
        this.agentMaxConcurrency = agentMaxConcurrency;
    }
    @Override
    public void transferSession(
            String sessionId,
            String sourceAgentId,
            String targetAgentId
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (sourceAgentId == null || sourceAgentId.isBlank()) {
            throw new IllegalArgumentException("当前客服不能为空");
        }
        if (targetAgentId == null || targetAgentId.isBlank()) {
            throw new IllegalArgumentException("目标客服不能为空");
        }
        if (sourceAgentId.equals(targetAgentId)) {
            throw new IllegalArgumentException("不能转接给当前客服自己");
        }

        String operationLockToken = chatRedisRepository.acquireSessionOperationLock(sessionId);
        if (operationLockToken == null) {
            throw new BusinessStateException("会话正在转接或结束，请稍后重试");
        }

        boolean redisTransferred = false;
        try {

        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
            throw new IllegalArgumentException("活动聊天会话不存在");
        }
        if (!sourceAgentId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("当前客服不是该会话分配的客服");
        }

        SysUser targetAgent = sysUserMapper.selectById(targetAgentId);
        Set<String> targetRoleCodes = targetAgent == null
                ? Set.of()
                : sysUserRoleMapper.findRoleCodesByUserId(targetAgentId);
        if (targetRoleCodes == null || !targetRoleCodes.contains("AGENT")) {
            throw new IllegalArgumentException("目标用户不是客服");
        }

        Long transferred = chatRedisRepository.execute(
                TRANSFER_SESSION_REDIS_SCRIPT,
                List.of(
                        RedisConstants.agentSessionsKey(sourceAgentId),
                        RedisConstants.agentSessionsKey(targetAgentId),
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.SESSION_META + sessionId,
                        RedisConstants.AGENT_LAST_ASSIGNED
                ),
                sessionId,
                sourceAgentId,
                targetAgentId,
                String.valueOf(RedisConstants.AGENT_MAX_CONCURRENCY),
                String.valueOf(System.currentTimeMillis())
        );
        if (!Long.valueOf(1L).equals(transferred)) {
            throw new IllegalArgumentException("目标客服不在线或已达到最大接待数量");
        }
        redisTransferred = true;

        Integer updatedRows = transactionTemplate.execute(status ->
                chatSessionMapper.transferSession(
                        sessionId,
                        sourceAgentId,
                        targetAgentId
                )
        );
        if (updatedRows == null || updatedRows != 1) {
            throw new BusinessStateException("会话归属已变化，转接失败");
        }
        // 数据库事务已经提交，之后即使通知失败也不能再回滚 Redis 归属。
        redisTransferred = false;

        session.setAgentId(targetAgentId);
        try {
            notifySessionTransferred(session, sourceAgentId, targetAgentId);
        } catch (RuntimeException exception) {
            log.error("会话已完成转接，但发送转接通知失败，sessionId={}", sessionId, exception);
        }
        log.info(
                "会话转接成功，sessionId={}，sourceAgentId={}，targetAgentId={}",
                sessionId,
                sourceAgentId,
                targetAgentId
        );
        } catch (RuntimeException exception) {
            if (redisTransferred) {
                try {
                    rollbackTransferredSessionInRedis(sessionId, sourceAgentId, targetAgentId);
                } catch (RuntimeException rollbackException) {
                    log.error(
                            "会话转接失败且Redis回滚失败，等待对账任务修复，sessionId={}",
                            sessionId,
                            rollbackException
                    );
                }
            }
            throw exception;
        } finally {
            chatRedisRepository.releaseSessionOperationLock(sessionId, operationLockToken);
        }
    }

    private void rollbackTransferredSessionInRedis(
            String sessionId,
            String sourceAgentId,
            String targetAgentId
    ) {
        chatRedisRepository.execute(
                ROLLBACK_TRANSFER_SESSION_REDIS_SCRIPT,
                List.of(
                        RedisConstants.agentSessionsKey(targetAgentId),
                        RedisConstants.agentSessionsKey(sourceAgentId),
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.SESSION_META + sessionId
                ),
                sessionId,
                targetAgentId,
                sourceAgentId
        );
    }

    private void notifySessionTransferred(
            ChatSession session,
            String sourceAgentId,
            String targetAgentId
    ) {
        ChatSessionDTO notice = ChatSessionDTO.fromEntity(
                session,
                "SESSION_TRANSFERRED"
        );
        notice.setReason("SESSION_TRANSFERRED");
        messagingTemplate.convertAndSendToUser(
                session.getUserId(),
                "/queue/chat",
                notice
        );
        messagingTemplate.convertAndSendToUser(
                sourceAgentId,
                "/queue/chat",
                notice
        );
        messagingTemplate.convertAndSendToUser(
                targetAgentId,
                "/queue/chat",
                notice
        );
    }

}
