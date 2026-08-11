package com.example.customerservice.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.constant.ChatMessageType;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.AssignResult;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.dto.ChatSessionDTO;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.IChatService;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceCallbacks;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatSessionTransferOperations;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Slf4j
abstract class ChatRoutingSessionSupport implements IChatService, ChatPresenceCallbacks {

    protected static final DefaultRedisScript<String>
            RESERVE_IDLE_AGENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local maxLoad = tonumber(ARGV[1]); " +
                            "local isVip = ARGV[4] == '1'; " +
                            "local reserved = tonumber(ARGV[5]); " +
                            "local excludedAgent = ARGV[6]; " +
                            "local vipAgentCount = redis.call('SCARD', KEYS[4]); " +
                            "local agents = redis.call('ZRANGEBYSCORE', KEYS[1], 0, maxLoad - 1); " +
                            "local preferVipSkill = isVip and vipAgentCount > 0; " +
                            "local selected = nil; local selectedLoad = nil; local selectedAt = nil; " +
                            "for _, agent in ipairs(agents) do " +
                            "local load = tonumber(redis.call('ZSCORE', KEYS[1], agent)); " +
                            "local vipSkilled = redis.call('SISMEMBER', KEYS[4], agent) == 1; " +
                            "local eligible = agent ~= excludedAgent; " +
                            "if preferVipSkill and not vipSkilled then eligible = false; end; " +
                            "if not isVip and vipSkilled and load >= math.max(0, maxLoad - reserved) then eligible = false; end; " +
                            "if eligible then " +
                            "local assignedAt = tonumber(redis.call('ZSCORE', KEYS[5], agent) or '0'); " +
                            "if not selected or load < selectedLoad or (load == selectedLoad and assignedAt < selectedAt) then " +
                            "selected = agent; selectedLoad = load; selectedAt = assignedAt; end; end; " +
                            "end; " +
                            "if not selected then return nil; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, selected); " +
                            "redis.call('ZADD', KEYS[5], ARGV[3], selected); " +
                            "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); " +
                            "redis.call('HSET', KEYS[3], ARGV[2], selected .. '|' .. ARGV[3] .. '|' .. ARGV[3]); " +
                            "return selected;",
                    String.class
            );

    protected static final DefaultRedisScript<Long>
            RESERVE_SPECIFIC_AGENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local score = redis.call('ZSCORE', KEYS[1], ARGV[1]); " +
                            "local maxLoad = tonumber(ARGV[2]); " +
                            "if not score or tonumber(score) >= maxLoad then return 0; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    protected static final DefaultRedisScript<Long>
            RELEASE_ASSIGNMENT_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('DEL', KEYS[1]); " +
                            "else return 0; end;",
                    Long.class
            );

    /**
     * 原子执行“检查客服容量 + 队首出队 + 增加客服负载”。
     * 会话落库失败时调用方会将用户重新入队并释放这一次负载。
     */
    protected static final DefaultRedisScript<String>
            RESERVE_AGENT_AND_DEQUEUE_SCRIPT =
            new DefaultRedisScript<>(
                            "local score = redis.call('ZSCORE', KEYS[1], ARGV[1]); " +
                            "local maxLoad = tonumber(ARGV[2]); " +
                            "if not score or tonumber(score) >= maxLoad then return nil; end; " +
                            "local first = redis.call('ZRANGE', KEYS[2], 0, 0); " +
                            "if #first == 0 then return nil; end; " +
                            "local vipLevel = tonumber(redis.call('HGET', KEYS[6], first[1]) or '0'); " +
                            "local vipSkilled = redis.call('SISMEMBER', KEYS[7], ARGV[1]) == 1; " +
                            "local reserved = tonumber(ARGV[4]); " +
                            "if vipLevel == 0 and vipSkilled and tonumber(score) >= math.max(0, maxLoad - reserved) then return nil; end; " +
                            "local users = redis.call('ZPOPMIN', KEYS[2], 1); " +
                            "if #users == 0 then return nil; end; " +
                            "local enqueuedAt = redis.call('ZSCORE', KEYS[5], users[1]) or ARGV[3]; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[8], ARGV[3], ARGV[1]); " +
                            "redis.call('ZADD', KEYS[3], ARGV[3], users[1]); " +
                            "redis.call('HSET', KEYS[4], users[1], ARGV[1] .. '|' .. users[2] .. '|' .. enqueuedAt); " +
                            "return users[1];",
                    String.class
            );

    /**
     * 分配失败时原子恢复原排队位置、客服负载和待确认分配记录。
     */
    protected static final DefaultRedisScript<Long>
            ROLLBACK_ASSIGNMENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local payload = redis.call('HGET', KEYS[4], ARGV[1]); " +
                            "if not payload then return 0; end; " +
                            "local split = string.find(payload, '|', 1, true); " +
                            "if not split then return 0; end; " +
                            "local secondSplit = string.find(payload, '|', split + 1, true); " +
                            "if not secondSplit then return 0; end; " +
                            "local storedAgent = string.sub(payload, 1, split - 1); " +
                            "local queueScore = tonumber(string.sub(payload, split + 1, secondSplit - 1)); " +
                            "local enqueuedAt = tonumber(string.sub(payload, secondSplit + 1)); " +
                            "if storedAgent ~= ARGV[2] then return 0; end; " +
                            "if ARGV[3] == '1' then " +
                            "redis.call('ZADD', KEYS[2], queueScore, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[5], enqueuedAt, ARGV[1]); end; " +
                            "local load = redis.call('ZSCORE', KEYS[1], ARGV[2]); " +
                            "if load then redis.call('ZADD', KEYS[1], math.max(0, tonumber(load) - 1), ARGV[2]); end; " +
                            "redis.call('ZREM', KEYS[3], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[4], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    protected static final DefaultRedisScript<Long>
            CLEAR_ASSIGNMENT_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[1], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[2], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    /**
     * MySQL会话创建成功后，原子提交全部Redis会话索引，
     * 并在同一次脚本中清除待确认分配记录。
     */
    protected static final DefaultRedisScript<Long>
            COMMIT_ACTIVE_SESSION_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1]); " +
                            "redis.call('SET', KEYS[2], ARGV[2]); " +
                            "redis.call('SET', KEYS[3], ARGV[3]); " +
                            "redis.call('SADD', KEYS[4], ARGV[1]); " +
                            "redis.call('HSET', KEYS[5], " +
                            "'status', ARGV[4], " +
                            "'createTime', ARGV[5], " +
                            "'vipLevel', ARGV[6], " +
                            "'agentId', ARGV[3]); " +
                            "redis.call('HDEL', KEYS[5], 'endTime'); " +
                            "redis.call('ZREM', KEYS[6], ARGV[2]); " +
                            "redis.call('HDEL', KEYS[7], ARGV[2]); " +
                            "redis.call('ZREM', KEYS[8], ARGV[2]); " +
                            "redis.call('ZADD', KEYS[9], 'NX', ARGV[7], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    /**
     * 同一VIP等级内按入队时间保持FIFO；VIP等级越高，score越小。
     * 真实入队时间单独写入QUEUE_ENQUEUED_AT，供超时清扫和等待时长统计使用。
     */
    protected static final DefaultRedisScript<Long>
            ENQUEUE_WAITING_USER_SCRIPT =
            new DefaultRedisScript<>(
                    "local now = tonumber(ARGV[2]); " +
                            "local vipLevel = tonumber(ARGV[3]); " +
                            "local offset = tonumber(ARGV[4]); " +
                            "local last = tonumber(redis.call('HGET', KEYS[2], ARGV[3]) or '0'); " +
                            "local score = now - vipLevel * offset; " +
                            "if last >= score then score = last + 0.001; end; " +
                            "redis.call('HSET', KEYS[2], ARGV[3], tostring(score)); " +
                            "redis.call('ZADD', KEYS[1], score, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[3], now, ARGV[1]); " +
                            "redis.call('HSET', KEYS[4], ARGV[1], ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    /** 在 Redis 中原子完成会话索引、状态和客服负载的收敛。 */
    protected static final DefaultRedisScript<Long>
            FINALIZE_SESSION_REDIS_SCRIPT =
            new DefaultRedisScript<>(
                    "local alreadyClosed = redis.call('HGET', KEYS[4], 'status') == 'CLOSED'; " +
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]); end; " +
                            "redis.call('DEL', KEYS[2]); redis.call('DEL', KEYS[3]); " +
                            "redis.call('SREM', KEYS[5], ARGV[1]); " +
                            "redis.call('HSET', KEYS[4], 'status', 'CLOSED', 'endTime', ARGV[2]); " +
                            "redis.call('EXPIRE', KEYS[4], ARGV[3]); " +
                            "if not alreadyClosed then " +
                            "if ARGV[5] == '1' then redis.call('ZREM', KEYS[6], ARGV[4]); " +
                            "else local score = redis.call('ZSCORE', KEYS[6], ARGV[4]); " +
                            "if score then redis.call('ZADD', KEYS[6], math.max(0, tonumber(score) - 1), ARGV[4]); end; end; end; " +
                            "redis.call('ZREM', KEYS[7], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[8], ARGV[1]); " +
                            "redis.call('ZREM', KEYS[9], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    protected static final DefaultRedisScript<Long>
            MARK_SESSION_FINALIZE_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]); " +
                            "redis.call('HSET', KEYS[2], ARGV[1], ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    protected static final DefaultRedisScript<Long>
            CLEAR_SESSION_FINALIZE_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[1], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[2], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    /** 原子切换会话所属客服，并同步两端客服负载与反向索引。 */
    protected static final DefaultRedisScript<Long>
            TRANSFER_SESSION_REDIS_SCRIPT =
            new DefaultRedisScript<>(
                    "local targetLoad = redis.call('ZSCORE', KEYS[3], ARGV[3]); " +
                            "if not targetLoad or tonumber(targetLoad) >= tonumber(ARGV[4]) then return 0; end; " +
                            "local sourceLoad = redis.call('ZSCORE', KEYS[3], ARGV[2]); " +
                            "if not sourceLoad then return 0; end; " +
                            "redis.call('SREM', KEYS[1], ARGV[1]); " +
                            "redis.call('SADD', KEYS[2], ARGV[1]); " +
                            "redis.call('ZADD', KEYS[3], math.max(0, tonumber(sourceLoad) - 1), ARGV[2]); " +
                            "redis.call('ZINCRBY', KEYS[3], 1, ARGV[3]); " +
                            "redis.call('ZADD', KEYS[6], ARGV[5], ARGV[3]); " +
                            "redis.call('SET', KEYS[4], ARGV[3]); " +
                            "redis.call('HSET', KEYS[5], 'agentId', ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    /** 数据库转接失败时恢复Redis中的会话归属和客服负载。 */
    protected static final DefaultRedisScript<Long>
            ROLLBACK_TRANSFER_SESSION_REDIS_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SREM', KEYS[1], ARGV[1]); " +
                            "redis.call('SADD', KEYS[2], ARGV[1]); " +
                            "local targetLoad = redis.call('ZSCORE', KEYS[3], ARGV[2]); " +
                            "if targetLoad then redis.call('ZADD', KEYS[3], math.max(0, tonumber(targetLoad) - 1), ARGV[2]); end; " +
                            "redis.call('ZINCRBY', KEYS[3], 1, ARGV[3]); " +
                            "redis.call('SET', KEYS[4], ARGV[3]); " +
                            "redis.call('HSET', KEYS[5], 'agentId', ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    protected final ChatRedisRepository chatRedisRepository;
    protected final ChatMessageOperations chatMessageOperations;
    protected final ChatPresenceOperations chatPresenceOperations;
    protected final ChatSessionTransferOperations chatSessionTransferOperations;
    protected final ChatSessionNotificationOperations chatSessionNotificationOperations;
    protected final ChatSessionMapper chatSessionMapper;
    protected final ChatMessageMapper chatMessageMapper;
    protected final ChatMessageReadMapper chatMessageReadMapper;
    protected final SimpMessagingTemplate messagingTemplate;
    protected final MessagePersistService messagePersistService;
    protected final ObjectMapper objectMapper;
    protected final SysUserRoleMapper sysUserRoleMapper;
    protected final SysUserMapper sysUserMapper;
    protected final long agentReconnectGraceMillis;
    protected final int vipReservedSlots;
    protected final long averageHandleSeconds;
    protected final long vipPriorityStepMillis;
    protected final long messageRecallWindowSeconds;
    protected final long messageEditWindowSeconds;

    protected ChatRoutingSessionSupport(
            ChatRedisRepository chatRedisRepository,
            ChatMessageOperations chatMessageOperations,
            ChatPresenceOperations chatPresenceOperations,
            ChatSessionTransferOperations chatSessionTransferOperations,
            ChatSessionNotificationOperations chatSessionNotificationOperations,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysUserMapper sysUserMapper,
            long agentReconnectGraceSeconds,
            int vipReservedSlots,
            long averageHandleSeconds,
            long vipPriorityStepSeconds,
            long messageRecallWindowSeconds,
            long messageEditWindowSeconds
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatMessageOperations = chatMessageOperations;
        this.chatPresenceOperations = chatPresenceOperations;
        this.chatSessionTransferOperations = chatSessionTransferOperations;
        this.chatSessionNotificationOperations = chatSessionNotificationOperations;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageReadMapper = chatMessageReadMapper;
        this.messagingTemplate = messagingTemplate;
        this.messagePersistService = messagePersistService;
        this.objectMapper = objectMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysUserMapper = sysUserMapper;
        this.agentReconnectGraceMillis = agentReconnectGraceSeconds * 1000L;
        this.vipReservedSlots = Math.max(
                0,
                Math.min(vipReservedSlots, RedisConstants.AGENT_MAX_CONCURRENCY)
        );
        this.averageHandleSeconds = Math.max(1L, averageHandleSeconds);
        this.vipPriorityStepMillis = Math.max(
                0L,
                vipPriorityStepSeconds * 1000L
        );
        this.messageRecallWindowSeconds = Math.max(1L, messageRecallWindowSeconds);
        this.messageEditWindowSeconds = Math.max(1L, messageEditWindowSeconds);
    }

    protected String acquireAssignmentLock(
            String userId
    ) {

        String lockToken =
                UUID.randomUUID().toString();

        Boolean acquired =
                chatRedisRepository.setValueIfAbsent(
                                RedisConstants.CHAT_ASSIGN_LOCK
                                        + userId,
                                lockToken,
                                RedisConstants
                                        .CHAT_ASSIGN_LOCK_TTL_SECONDS,
                                TimeUnit.SECONDS
                        );

        return Boolean.TRUE.equals(acquired)
                ? lockToken
                : null;
    }

    protected String acquireSessionOperationLock(String sessionId) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = chatRedisRepository.setValueIfAbsent(
                RedisConstants.SESSION_OPERATION_LOCK + sessionId,
                lockToken,
                RedisConstants.SESSION_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(acquired) ? lockToken : null;
    }

    protected void releaseSessionOperationLock(
            String sessionId,
            String lockToken
    ) {
        if (sessionId == null || lockToken == null) {
            return;
        }
        chatRedisRepository.execute(
                RELEASE_ASSIGNMENT_LOCK_SCRIPT,
                Collections.singletonList(
                        RedisConstants.SESSION_OPERATION_LOCK + sessionId
                ),
                lockToken
        );
    }


    protected void releaseAssignmentLock(
            String userId,
            String lockToken
    ) {

        if (
                userId == null ||
                        lockToken == null
        ) {
            return;
        }

        chatRedisRepository.execute(
                RELEASE_ASSIGNMENT_LOCK_SCRIPT,
                Collections.singletonList(
                        RedisConstants.CHAT_ASSIGN_LOCK
                                + userId
                ),
                lockToken
        );
    }


    protected void fillAvailableAgentCapacity(
            String agentId
    ) {
        for (
                int slot = 0;
                slot < RedisConstants.AGENT_MAX_CONCURRENCY;
                slot++
        ) {
            Long waitingCount =
                    chatRedisRepository.sortedSetCardinality(
                                    RedisConstants.QUEUE_PENDING
                            );

            Double currentLoad =
                    chatRedisRepository.sortedSetScore(
                                    RedisConstants.AGENT_LOAD,
                                    agentId
                            );

            if (
                    waitingCount == null ||
                            waitingCount <= 0 ||
                            currentLoad == null ||
                            currentLoad
                                    >= RedisConstants
                                    .AGENT_MAX_CONCURRENCY
            ) {
                return;
            }

            processNextWaitingUser(
                    agentId
            );
        }
    }


    protected boolean reserveSpecificAgent(
            String agentId
    ) {

        Double currentLoad = chatRedisRepository.sortedSetScore(
                RedisConstants.AGENT_LOAD,
                agentId
        );
        return currentLoad != null
                && currentLoad < RedisConstants.AGENT_MAX_CONCURRENCY;
    }


    protected void releaseAgentLoad(
            String agentId,
            boolean agentDisconnected
    ) {

        if (
                agentId == null ||
                        agentId.isBlank()
        ) {

            return;
        }


        if (agentDisconnected) {

            chatRedisRepository.sortedSetRemove(
                            RedisConstants.AGENT_LOAD,
                            agentId
                    );

            return;
        }


        Double currentLoad =
                chatRedisRepository.sortedSetScore(
                                RedisConstants.AGENT_LOAD,
                                agentId
                        );


        if (currentLoad == null) {

            return;
        }


        double newLoad =
                Math.max(
                        0,
                        currentLoad - 1
                );


        chatRedisRepository.sortedSetAdd(
                        RedisConstants.AGENT_LOAD,
                        agentId,
                        newLoad
                );
    }

    protected boolean finalizeSession(
            ChatSession session,
            String expectedAgentId
    ) {
        return finalizeSession(session, expectedAgentId, null);
    }

    protected boolean finalizeSession(
            ChatSession session,
            String expectedAgentId,
            Long inactivityCutoffMillis
    ) {

        if (session == null) {

            return false;
        }

        String operationLockToken = null;
        for (int attempt = 0; attempt < 100 && operationLockToken == null; attempt++) {
            operationLockToken = acquireSessionOperationLock(session.getId());
            if (operationLockToken == null) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        if (operationLockToken == null) {
            log.warn("Session operation lock did not become available: {}", session.getId());
            return false;
        }

        try {
        ChatSession latestSession = chatSessionMapper.selectById(session.getId());
        if (latestSession == null
                || !ChatConstants.SESSION_STATUS_ACTIVE.equals(latestSession.getStatus())) {
            return false;
        }
        if (expectedAgentId != null
                && !expectedAgentId.equals(latestSession.getAgentId())) {
            return false;
        }
        if (inactivityCutoffMillis != null) {
            Double lastActivity = chatRedisRepository.sortedSetScore(
                    RedisConstants.SESSION_LAST_ACTIVITY,
                    latestSession.getId()
            );
            if (lastActivity == null || lastActivity > inactivityCutoffMillis) {
                return false;
            }
        }
        session = latestSession;


        LocalDateTime endTime =
                LocalDateTime.now();

        markSessionFinalizePending(session, endTime);

        int updatedRows =
                chatSessionMapper.endSession(

                        session.getId(),

                        endTime
                );


        if (updatedRows == 0) {
            ChatSession currentSession =
                    chatSessionMapper.selectById(session.getId());
            if (currentSession == null) {
                clearSessionFinalizePending(session.getId());
                return false;
            }
            if (!ChatConstants.SESSION_STATUS_CLOSED.equals(
                    currentSession.getStatus()
            )) {
                return false;
            }
            session = currentSession;
            if (currentSession.getEndTime() != null) {
                endTime = currentSession.getEndTime();
            }
        }


        session.setStatus(
                RedisConstants
                        .SESSION_STATUS_CLOSED
        );


        session.setEndTime(
                endTime
        );

        recordVipResolveTime(
                session,
                endTime
        );


        String sessionId =
                session.getId();


        /*
         * 用户不再具有活动会话。
         */
        /*
         * 删除会话双方反向映射，
         * 防止无TTL Key永久残留。
         */
        /*
         * 会话元数据不立即删除，
         * 标记为CLOSED并保留24小时。
         */
        applySessionFinalizationRedis(session, endTime, false);


        return true;
        } finally {
            releaseSessionOperationLock(session.getId(), operationLockToken);
        }
    }

    protected void markSessionFinalizePending(
            ChatSession session,
            LocalDateTime endTime
    ) {
        String payload = String.join(
                "|",
                session.getUserId(),
                session.getAgentId(),
                endTime.toString()
        );
        chatRedisRepository.execute(
                MARK_SESSION_FINALIZE_PENDING_SCRIPT,
                List.of(
                        RedisConstants.SESSION_FINALIZE_PENDING,
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD
                ),
                session.getId(),
                String.valueOf(System.currentTimeMillis()),
                payload
        );
    }

    protected void clearSessionFinalizePending(String sessionId) {
        chatRedisRepository.execute(
                CLEAR_SESSION_FINALIZE_PENDING_SCRIPT,
                List.of(
                        RedisConstants.SESSION_FINALIZE_PENDING,
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD
                ),
                sessionId
        );
    }

    protected void applySessionFinalizationRedis(
            ChatSession session,
            LocalDateTime endTime,
            boolean agentDisconnected
    ) {
        String sessionId = session.getId();
        chatRedisRepository.execute(
                FINALIZE_SESSION_REDIS_SCRIPT,
                List.of(
                        RedisConstants.USER_ACTIVE_SESSION + session.getUserId(),
                        RedisConstants.SESSION_USER + sessionId,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.SESSION_META + sessionId,
                        RedisConstants.agentSessionsKey(session.getAgentId()),
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.SESSION_FINALIZE_PENDING,
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD,
                        RedisConstants.SESSION_LAST_ACTIVITY
                ),
                sessionId,
                endTime.toString(),
                String.valueOf(TimeUnit.HOURS.toSeconds(
                        RedisConstants.SESSION_META_TTL_HOURS
                )),
                session.getAgentId(),
                agentDisconnected ? "1" : "0"
        );
    }
    /**
     * 重连时只通知当前用户。
     */
    protected void notifyUserSession(ChatSession session) {
        chatSessionNotificationOperations.notifyUserSession(
                session,
                getVipLevel(session.getUserId())
        );
    }

    protected void notifyWaitingUser(String userId, Long waitingPosition) {
        chatSessionNotificationOperations.notifyWaitingUser(
                userId,
                createWaitingResult(userId, waitingPosition)
        );
    }

    protected Long getWaitingPosition(String userId) {
        Long rank = chatRedisRepository.sortedSetRank(
                RedisConstants.QUEUE_PENDING,
                userId
        );
        return rank == null ? null : rank + 1;
    }

    protected Long estimateWaitingSeconds(Long waitingPosition) {
        if (waitingPosition == null || waitingPosition <= 0) {
            return null;
        }
        Long onlineAgentCount = chatRedisRepository.sortedSetCardinality(
                RedisConstants.AGENT_LOAD
        );
        if (onlineAgentCount == null || onlineAgentCount <= 0) {
            return null;
        }
        long serviceRounds = (waitingPosition + onlineAgentCount - 1)
                / onlineAgentCount;
        return Math.multiplyExact(serviceRounds, averageHandleSeconds);
    }
    public void refreshWaitingPositions() {
        Set<String> userIds = chatRedisRepository.sortedSetRange(
                RedisConstants.QUEUE_PENDING,
                0,
                99
        );
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            notifyWaitingUser(userId, getWaitingPosition(userId));
        }
    }
    @Override
    public void registerOnline(String userId, String wsSessionId) {
        chatPresenceOperations.registerOnline(userId, wsSessionId);
    }

    @Override
    public void handleAgentReconnectGraceTimeout(String agentId) {
        chatPresenceOperations.handleAgentReconnectGraceTimeout(agentId);
    }

    @Override
    public AssignResult reconnectUser(String userId) {
        return onUserConnected(userId);
    }

    @Override
    public void restoreAgentOnline(String agentId) {
        agentOnline(agentId);
    }

    @Override
    public boolean finalizePresenceSession(ChatSession session, String expectedAgentId) {
        return finalizeSession(session, expectedAgentId);
    }

    @Override
    public int resolveVipLevel(String userId) {
        return getVipLevel(userId);
    }

    @Override
    public void publishSessionClosed(String userId, String sessionId, String reason) {
        notifySessionClosed(userId, sessionId, reason);
    }

    @Override
    public void reassignDisconnectedUser(String userId) {
        dequeueAndReassign(userId);
    }
    protected int getVipLevel(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getVipLevel() == null) {
            return 0;
        }
        return Math.max(
                0,
                Math.min(user.getVipLevel(), 5)
        );
    }

    protected AssignResult withVipLevel(
            AssignResult result,
            int vipLevel
    ) {
        result.setVipLevel(vipLevel);
        return result;
    }

    protected AssignResult createWaitingResult(
            String userId,
            Long waitingPosition
    ) {
        int vipLevel = getVipLevel(userId);
        Long onlineAgentCount = chatRedisRepository.sortedSetCardinality(
                RedisConstants.AGENT_LOAD
        );
        boolean callbackRequired =
                vipLevel > 0
                        && (onlineAgentCount == null || onlineAgentCount == 0);
        AssignResult result =
                callbackRequired
                        ? AssignResult.vipCallbackRequired(waitingPosition)
                        : AssignResult.waiting(
                                waitingPosition,
                                estimateWaitingSeconds(waitingPosition)
                        );
        if (callbackRequired) {
            chatRedisRepository.sortedSetAdd(
                    RedisConstants.VIP_CALLBACK_PENDING,
                    userId,
                    System.currentTimeMillis()
            );
        }
        return withVipLevel(result, vipLevel);
    }

    protected void recordVipWaitTime(ChatSession session) {
        String userId = session.getUserId();
        Double enqueuedAt = chatRedisRepository.sortedSetScore(
                RedisConstants.QUEUE_ENQUEUED_AT,
                userId
        );
        chatRedisRepository.sortedSetRemove(
                RedisConstants.QUEUE_ENQUEUED_AT,
                userId
        );
        chatRedisRepository.hashDelete(
                RedisConstants.QUEUE_VIP_LEVEL,
                userId
        );
        chatRedisRepository.sortedSetRemove(
                RedisConstants.VIP_CALLBACK_PENDING,
                userId
        );
        if (enqueuedAt == null || getVipLevel(userId) <= 0) {
            return;
        }
        long waitMillis = Math.max(
                0L,
                System.currentTimeMillis() - enqueuedAt.longValue()
        );
        chatRedisRepository.sortedSetAdd(
                RedisConstants.STATS_VIP_WAIT,
                session.getId(),
                waitMillis
        );
    }

    protected void recordVipResolveTime(
            ChatSession session,
            LocalDateTime endTime
    ) {
        if (getVipLevel(session.getUserId()) <= 0
                || session.getCreateTime() == null
                || endTime == null) {
            return;
        }
        long resolveMillis = Math.max(
                0L,
                Duration.between(
                        session.getCreateTime(),
                        endTime
                ).toMillis()
        );
        chatRedisRepository.sortedSetAdd(
                RedisConstants.STATS_VIP_RESOLVE,
                session.getId(),
                resolveMillis
        );
    }

    public abstract void agentOnline(String agentId);

    public abstract AssignResult onUserConnected(String userId);

    public abstract void processNextWaitingUser(String agentId);

    protected abstract void dequeueAndReassign(String agentId);

    protected abstract void notifySessionClosed(String userId, String sessionId, String reason);
}
