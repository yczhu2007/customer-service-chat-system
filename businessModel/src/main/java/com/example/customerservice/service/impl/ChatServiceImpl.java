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
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.service.IChatService;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class ChatServiceImpl implements IChatService {

    private static final DefaultRedisScript<String>
            RESERVE_IDLE_AGENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local maxLoad = tonumber(ARGV[1]); " +
                            "local isVip = ARGV[4] == '1'; " +
                            "local reserved = tonumber(ARGV[5]); " +
                            "local vipAgentCount = redis.call('SCARD', KEYS[4]); " +
                            "local agents = redis.call('ZRANGEBYSCORE', KEYS[1], 0, maxLoad - 1); " +
                            "local selected = nil; " +
                            "if isVip and vipAgentCount > 0 then " +
                            "for _, agent in ipairs(agents) do " +
                            "if redis.call('SISMEMBER', KEYS[4], agent) == 1 then selected = agent; break; end; " +
                            "end; end; " +
                            "for _, agent in ipairs(agents) do " +
                            "if selected then break; end; " +
                            "local load = tonumber(redis.call('ZSCORE', KEYS[1], agent)); " +
                            "local vipSkilled = redis.call('SISMEMBER', KEYS[4], agent) == 1; " +
                            "if isVip then " +
                            "selected = agent; break; " +
                            "else " +
                            "local regularLimit = maxLoad; " +
                            "if vipSkilled then regularLimit = math.max(0, maxLoad - reserved); end; " +
                            "if load < regularLimit then selected = agent; break; end; " +
                            "end; end; " +
                            "if not selected then return nil; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, selected); " +
                            "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); " +
                            "redis.call('HSET', KEYS[3], ARGV[2], selected .. '|' .. ARGV[3] .. '|' .. ARGV[3]); " +
                            "return selected;",
                    String.class
            );

    private static final DefaultRedisScript<Long>
            RESERVE_SPECIFIC_AGENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local score = redis.call('ZSCORE', KEYS[1], ARGV[1]); " +
                            "local maxLoad = tonumber(ARGV[2]); " +
                            "if not score or tonumber(score) >= maxLoad then return 0; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    private static final DefaultRedisScript<Long>
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
    private static final DefaultRedisScript<String>
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
                            "redis.call('ZADD', KEYS[3], ARGV[3], users[1]); " +
                            "redis.call('HSET', KEYS[4], users[1], ARGV[1] .. '|' .. users[2] .. '|' .. enqueuedAt); " +
                            "return users[1];",
                    String.class
            );

    /**
     * 分配失败时原子恢复原排队位置、客服负载和待确认分配记录。
     */
    private static final DefaultRedisScript<Long>
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

    private static final DefaultRedisScript<Long>
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
    private static final DefaultRedisScript<Long>
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
                            "return 1;",
                    Long.class
            );

    /**
     * 同一VIP等级内按入队时间保持FIFO；VIP等级越高，score越小。
     * 真实入队时间单独写入QUEUE_ENQUEUED_AT，供超时清扫和等待时长统计使用。
     */
    private static final DefaultRedisScript<Long>
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
    private static final DefaultRedisScript<Long>
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
                            "return 1;",
                    Long.class
            );

    private static final DefaultRedisScript<Long>
            MARK_SESSION_FINALIZE_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]); " +
                            "redis.call('HSET', KEYS[2], ARGV[1], ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    private static final DefaultRedisScript<Long>
            CLEAR_SESSION_FINALIZE_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[1], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[2], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    /** 原子切换会话所属客服，并同步两端客服负载与反向索引。 */
    private static final DefaultRedisScript<Long>
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
                            "redis.call('SET', KEYS[4], ARGV[3]); " +
                            "redis.call('HSET', KEYS[5], 'agentId', ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    /** 数据库转接失败时恢复Redis中的会话归属和客服负载。 */
    private static final DefaultRedisScript<Long>
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

    private final StringRedisTemplate redisTemplate;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessagePersistService messagePersistService;
    private final ObjectMapper objectMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final long agentReconnectGraceMillis;
    private final int vipReservedSlots;
    private final long averageHandleSeconds;
    private final long vipPriorityStepMillis;

    public ChatServiceImpl(
            StringRedisTemplate redisTemplate,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysUserMapper sysUserMapper,
            @Value("${app.chat.agent-reconnect-grace-seconds:20}")
            long agentReconnectGraceSeconds,
            @Value("${app.chat.vip.reserved-slots:1}")
            int vipReservedSlots,
            @Value("${app.chat.queue.average-handle-seconds:300}")
            long averageHandleSeconds,
            @Value("${app.chat.queue.vip-priority-step-seconds:30}")
            long vipPriorityStepSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
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
    }

    @Override
    public AssignResult onUserConnected(
            String userId
    ) {

        if (
                userId == null ||
                        userId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }

        int vipLevel = getVipLevel(userId);

        String assignmentLockToken =
                acquireAssignmentLock(
                        userId
                );


        if (assignmentLockToken == null) {
            log.info(
                    "用户分配正在处理中，忽略重复请求，userId："
                            + userId
            );
            return withVipLevel(
                    AssignResult.processing(),
                    vipLevel
            );
        }


        try {
        String activeSessionKey =
                RedisConstants.USER_ACTIVE_SESSION
                        + userId;


        String cachedSessionId =
                redisTemplate.opsForValue()
                        .get(
                                activeSessionKey
                        );


        if (
                cachedSessionId != null &&
                        !cachedSessionId.isBlank()
        ) {

            ChatSession cachedSession =
                    chatSessionMapper.selectById(
                            cachedSessionId
                    );


            if (
                    cachedSession != null &&
                            ChatConstants.SESSION_STATUS_ACTIVE.equals(
                                    cachedSession.getStatus()
                            ) &&
                            userId.equals(
                                    cachedSession.getUserId()
                            )
            ) {

                /*
                 * 补齐可能缺失的会话索引，
                 * 并把恢复结果推送给当前用户。
                 */
                cacheActiveSessionState(
                        cachedSession
                );


                notifyUserSession(
                        cachedSession
                );


                return withVipLevel(
                        AssignResult.reconnected(cachedSession),
                        vipLevel
                );
            }


            /*
             * Redis索引与MySQL实际状态不一致，
             * 删除失效索引后继续使用MySQL兜底。
             */
            redisTemplate.delete(
                    activeSessionKey
            );
        }


        /*
         * Redis没有有效会话时查询MySQL，
         * 防止Redis数据丢失后重复创建会话。
         */
        ChatSession oldSession =
                chatSessionMapper.findActiveByUserId(
                        userId
                );


        if (oldSession != null) {

            cacheActiveSessionState(
                    oldSession
            );


            /*
             * 订阅事件没有HTTP返回值，
             * 所以通过WebSocket把旧会话推给用户。
             */
            notifyUserSession(
                    oldSession
            );


            return withVipLevel(
                    AssignResult.reconnected(oldSession),
                    vipLevel
            );
        }
        String agentId =
                findIdleAgent(userId);
        if (agentId == null) {

            enqueueWaitingUser(
                    userId
            );

            Long waitingPosition = getWaitingPosition(userId);
            notifyWaitingUser(userId, waitingPosition);

            return createWaitingResult(
                    userId,
                    waitingPosition
            );
        }
        ChatSession session;


        try {
            session =
                    createSession(
                            userId,
                            agentId
                    );
        } catch (RuntimeException exception) {
            handleSessionCreationFailure(userId, agentId, false);
            throw exception;
        }

        notifyBothParties(
                session
        );


        return withVipLevel(
                AssignResult.assigned(session),
                vipLevel
        );
        } finally {
            releaseAssignmentLock(
                    userId,
                    assignmentLockToken
            );
        }
    }
    @Override
    public ChatSession createSession(String userId,String agentId){


        ChatSession session = new ChatSession();

        session.setId(UUID.randomUUID().toString());

        session.setUserId(userId);

        session.setAgentId(agentId);

        session.setStatus(ChatConstants.SESSION_STATUS_ACTIVE);

        session.setCreateTime(LocalDateTime.now());


        int savedRows =
                chatSessionMapper.insert(
                        session
                );


        if (savedRows != 1) {

            throw new IllegalStateException(
                    "创建聊天会话失败"
            );
        }


        /*
         * MySQL创建成功后，
         * 写入Redis活动会话索引
         */
        cacheActiveSessionState(
                session
        );

        recordVipWaitTime(session);

        log.info(
                "聊天会话创建成功，sessionId："
                        + session.getId()
                        + "，用户："
                        + userId
                        + "，客服："
                        + agentId
        );


        return session;
    }
    /**
     * 将活动会话状态写入Redis
     */
    private void cacheActiveSessionState(
            ChatSession session
    ) {

        String sessionId =
                session.getId();

        String userId =
                session.getUserId();

        String agentId =
                session.getAgentId();


        /*
         * 用户 → 当前活动会话
         *
         * user:active:session:U990
         * → 聊天会话ID
         */
        String metaKey =
                RedisConstants.SESSION_META
                        + sessionId;

        Long committed = redisTemplate.execute(
                COMMIT_ACTIVE_SESSION_SCRIPT,
                List.of(
                        RedisConstants.USER_ACTIVE_SESSION + userId,
                        RedisConstants.SESSION_USER + sessionId,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.agentSessionsKey(agentId),
                        metaKey,
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                        RedisConstants.QUEUE_PENDING
                ),
                sessionId,
                userId,
                agentId,
                session.getStatus(),
                session.getCreateTime() == null
                        ? ""
                        : session.getCreateTime().toString(),
                String.valueOf(getVipLevel(userId))
        );
        if (!Long.valueOf(1L).equals(committed)) {
            throw new IllegalStateException("提交活动会话Redis状态失败");
        }


        log.info(
                "活动会话Redis索引已写入，sessionId："
                        + sessionId
        );
    }

    @Override
    public void agentOnline(
            String agentId
    ) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "agentId不能为空"
            );
        }

        Set<String> indexedSessionIds = redisTemplate.opsForSet().members(
                RedisConstants.agentSessionsKey(agentId)
        );
        List<ChatSession> activeSessions = new ArrayList<>();
        if (indexedSessionIds != null && !indexedSessionIds.isEmpty()) {
            for (String sessionId : indexedSessionIds) {
                ChatSession session = chatSessionMapper.selectById(sessionId);
                if (session != null && ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
                    activeSessions.add(session);
                } else {
                    redisTemplate.opsForSet().remove(
                            RedisConstants.agentSessionsKey(agentId),
                            sessionId
                    );
                }
            }
        }
        if (activeSessions.isEmpty()) {
            // Redis 索引可能因历史版本或缓存丢失而不存在，保留数据库兜底。
            activeSessions = chatSessionMapper.selectList(
                    Wrappers.<ChatSession>lambdaQuery()
                            .eq(ChatSession::getAgentId, agentId)
                            .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_ACTIVE)
                            .orderByAsc(ChatSession::getCreateTime)
            );
        }

        for (ChatSession activeSession : activeSessions) {
            redisTemplate.opsForSet().add(
                    RedisConstants.agentSessionsKey(agentId),
                    activeSession.getId()
            );
        }

        redisTemplate.opsForZSet().add(
                RedisConstants.AGENT_LOAD,
                agentId,
                activeSessions.size()
        );

        activeSessions.forEach(
                this::notifyBothParties
        );

        log.info(
                "客服上线，agentId={}，已恢复活动会话数={}",
                agentId,
                activeSessions.size()
        );

        fillAvailableAgentCapacity(
                agentId
        );
    }



    @Override
    @Transactional
    public void agentOffline(String agentId) {

        if (
                agentId == null ||
                        agentId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "agentId不能为空"
            );
        }


        /*
         * Manual offline changes service availability, not WebSocket connectivity.
         * Keeping the connection mapping allows the same client to go online again
         * and still lets a later disconnect event identify and clean up this agent.
         */
        redisTemplate.opsForZSet().remove(
                RedisConstants.AGENT_LOAD,
                agentId
        );
        redisTemplate.opsForZSet().remove(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId
        );
        handleAgentDisconnect(
                agentId,
                ChatConstants.REASON_AGENT_OFFLINE
        );
    }
    @Override
    public String findIdleAgent(String userId) {

        /*
         * 原子查询并占用尚未达到最大并发数的最低负载客服。
         *
         * score = 0：
         * 当前没有活动会话，可以接用户。
         *
         * score为1到4：
         * 仍有容量，可以继续分配；score达到5后停止分配。
         */
        int vipLevel = getVipLevel(userId);
        return redisTemplate.execute(
                RESERVE_IDLE_AGENT_SCRIPT,
                List.of(
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                        RedisConstants.AGENT_SKILL_VIP
                ),
                String.valueOf(
                        RedisConstants
                                .AGENT_MAX_CONCURRENCY
                ),
                userId,
                String.valueOf(System.currentTimeMillis()),
                vipLevel > 0 ? "1" : "0",
                String.valueOf(vipReservedSlots)
        );
    }


    @Override
    public void enqueueWaitingUser(
            String userId
    ) {

        /*
         * 先删除该用户可能存在的旧排队记录，
         * 防止重复订阅造成重复入队。
         */
        redisTemplate.execute(
                ENQUEUE_WAITING_USER_SCRIPT,
                List.of(
                        RedisConstants.QUEUE_PENDING,
                        RedisConstants.QUEUE_SEQUENCE,
                        RedisConstants.QUEUE_ENQUEUED_AT,
                        RedisConstants.QUEUE_VIP_LEVEL
                ),
                userId,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(getVipLevel(userId)),
                String.valueOf(vipPriorityStepMillis)
        );
        refreshWaitingPositions();
    }
    @Override
    public void notifyBothParties(ChatSession session) {
        AssignResult notice =
                AssignResult.assigned(
                        session
                );
        notice.setVipLevel(
                getVipLevel(session.getUserId())
        );


        /*
         * 通知用户
         *
         * 用户订阅：
         * /user/queue/chat
         */
        messagingTemplate.convertAndSendToUser(
                session.getUserId(),
                "/queue/chat",
                notice
        );


        /*
         * 通知客服
         *
         * 客服也订阅：
         * /user/queue/chat
         */
        messagingTemplate.convertAndSendToUser(
                session.getAgentId(),
                "/queue/chat",
                notice
        );


        log.info(
                "会话创建通知已发送，用户："
                        + session.getUserId()
                        + "，客服："
                        + session.getAgentId()
        );
    }

    @Override
    @Transactional
    public void handleDisconnect(
            String wsSessionId
    ) {

        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            return;
        }
        String userId =
                redisTemplate.opsForValue()
                        .get(
                                RedisConstants.WS_SESSION
                                        + wsSessionId
                        );


        if (
                userId == null ||
                        userId.isBlank()
        ) {

            /*
             * 映射已经不存在，
             * 说明该连接可能已经被清理。
             */
            return;
        }
        String currentWsSessionId =
                redisTemplate.opsForValue()
                        .get(
                                RedisConstants.USER_WS
                                        + userId
                        );


        /*
         * 当前用户已经建立了更新的连接。
         *
         * 旧连接断开时，只删除旧连接的反向映射，
         * 不能清理用户当前在线状态，也不能结束聊天会话。
         */
        if (
                currentWsSessionId != null &&
                        !wsSessionId.equals(
                                currentWsSessionId
                        )
        ) {

            redisTemplate.delete(
                    RedisConstants.WS_SESSION
                            + wsSessionId
            );


            log.info(
                    "忽略旧WebSocket连接断开，用户："
                            + userId
                            + "，旧连接："
                            + wsSessionId
                            + "，当前连接："
                            + currentWsSessionId
            );


            return;
        }


        /*
         * 当前连接真正断开，
         * 执行完整业务清理。
         */
        handleOffline(
                userId,
                wsSessionId,
                ChatConstants.REASON_WEBSOCKET_DISCONNECT
        );
    }
    /**
     * 处理聊天消息
     */
    @Override
    public int handleMessage(ChatMessage message) {
        if (
                message.getSessionId() == null ||
                        message.getSessionId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }


        if (
                message.getSenderId() == null ||
                        message.getSenderId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "senderId不能为空"
            );
        }


        if (
                message.getClientMsgId() == null ||
                        message.getClientMsgId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "clientMsgId不能为空"
            );
        }


        if (
                message.getContent() == null ||
                        message.getContent().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "消息内容不能为空"
            );
        }

        ChatMessageType messageType =
                ChatMessageContentValidator.validate(
                        message.getType(),
                        message.getContent()
                );
        message.setType(messageType.name());
        String dedupKey =
                RedisConstants.CLIENT_MSG_DEDUP
                        + message.getClientMsgId();


        Boolean firstSend =
                redisTemplate.opsForValue()
                        .setIfAbsent(
                                dedupKey,
                                "1",
                                24,
                                TimeUnit.HOURS
                        );


        if (!Boolean.TRUE.equals(firstSend)) {

            log.info(
                    "检测到重复消息，clientMsgId："
                            + message.getClientMsgId()
            );

            return 0;
        }
        ChatSession session =
                chatSessionMapper.selectById(
                        message.getSessionId()
                );


        if (session == null) {

            // 当前消息处理失败，删除刚才写入的去重Key
            redisTemplate.delete(dedupKey);

            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }


        if (!ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {

            redisTemplate.delete(dedupKey);

            throw new IllegalArgumentException(
                    "聊天会话已经结束"
            );
        }


        /*
         * 判断发送者是不是会话参与者，
         *    同时确定发送者角色
         */
        if (
                message.getSenderId()
                        .equals(session.getUserId())
        ) {

            message.setSenderRole("USER");

        } else if (
                message.getSenderId()
                        .equals(session.getAgentId())
        ) {

            message.setSenderRole("AGENT");

        } else {

            redisTemplate.delete(dedupKey);

            throw new IllegalArgumentException(
                    "当前用户不属于这个聊天会话"
            );
        }
        message.setId(
                UUID.randomUUID().toString()
        );


        message.setCreateTime(
                LocalDateTime.now()
        );


        /*
         * 在提交异步任务前先记录待落库状态；应用崩溃后由定时任务补偿。
         */
        messagePersistService.markPending(
                message
        );
        cacheMessage(message);


        ChatMessageDTO receivedAcknowledgement =
                ChatMessageDTO.fromEntity(message);
        receivedAcknowledgement.setAckStatus("RECEIVED");
        messagingTemplate.convertAndSendToUser(
                message.getSenderId(),
                "/queue/chat",
                receivedAcknowledgement
        );
        routeAndPush(message);
        messagePersistService.persistMessageAsync(
                message
        );


        log.info(
                "消息处理完成，messageId："
                        + message.getId()
        );


        return 1;
    }
    /**
     * 把消息放入Redis热缓存
     */
    @Override
    public void cacheMessage(
            ChatMessage message
    ) {

        String messageKey =
                RedisConstants.SESSION_MSG
                        + message.getSessionId();


        final String messageJson;

        try {

            /*
             * 保存完整ChatMessage：
             *
             * id
             * sessionId
             * senderId
             * senderRole
             * type
             * content
             * clientMsgId
             * createTime
             */
            messageJson =
                    objectMapper.writeValueAsString(
                            message
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "消息序列化失败，messageId："
                            + message.getId(),
                    e
            );
        }


        /*
         * 使用rightPush保证消息按产生顺序排列。
         */
        redisTemplate.opsForList()
                .rightPush(
                        messageKey,
                        messageJson
                );


        /*
         * 只保留最后200条。
         *
         * -200到-1表示列表末尾最近200条。
         */
        redisTemplate.opsForList()
                .trim(
                        messageKey,
                        -RedisConstants
                                .SESSION_MESSAGE_LIMIT,
                        -1
                );
    }
    /**
     * 保存尚未确认的消息
     * 无论接收者当前在线还是离线，
     * 消息都会先进入这个列表。
     * 收到客户端ACK以后再删除。
     */
    private String cacheOfflineMessage(
            String receiverId,
            ChatMessage message
    ) {

        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + receiverId;


        try {

            /*
             * 将完整消息转换为JSON
             */
            String messageJson =
                    objectMapper.writeValueAsString(
                            message
                    );


            /*
             * 将消息加入接收者的待确认列表
             */
            redisTemplate.opsForList()
                    .rightPush(
                            offlineKey,
                            messageJson
                    );


            /*
             * 最多保留最近200条
             */
            redisTemplate.opsForList()
                    .trim(
                            offlineKey,
                            -RedisConstants
                                    .OFFLINE_MSG_MAX_COUNT,
                            -1
                    );


            /*
             * 消息最多保留7天
             */
            redisTemplate.expire(
                    offlineKey,
                    RedisConstants
                            .OFFLINE_MSG_TTL_DAYS,
                    TimeUnit.DAYS
            );


            log.info(
                    "待确认消息已保存，接收者："
                            + receiverId
                            + "，messageId："
                            + message.getId()
            );


            return messageJson;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "消息序列化失败",
                    e
            );
        }
    }
    @Override
    public void routeAndPush(
            ChatMessage message
    ) {
        ChatSession session =
                chatSessionMapper.selectById(
                        message.getSessionId()
                );


        if (session == null) {

            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }
        String receiverId;


        if (
                message.getSenderId()
                        .equals(
                                session.getUserId()
                        )
        ) {

            receiverId =
                    session.getAgentId();

        } else if (
                message.getSenderId()
                        .equals(
                                session.getAgentId()
                        )
        ) {

            receiverId =
                    session.getUserId();

        } else {

            throw new IllegalArgumentException(
                    "发送者不属于这个聊天会话"
            );
        }


        /*
         * 消息先进入待确认列表
         *
         * 只有收到客户端ACK后才删除。
         */
        cacheOfflineMessage(
                receiverId,
                message
        );
        String wsSessionId =
                redisTemplate.opsForValue().get(
                        RedisConstants.USER_WS
                                + receiverId
                );


        /*
         * 接收者离线
         *
         * 消息已经保存在Redis中，
         * 等待用户上线后拉取。
         */
        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            log.info(
                    "接收者当前离线，消息等待上线拉取："
                            + receiverId
            );


            return;
        }
        messagingTemplate.convertAndSendToUser(
                receiverId,
                "/queue/chat",
                ChatMessageDTO.fromEntity(
                        message
                )
        );


        log.info(
                "消息已实时推送，等待ACK，接收者："
                        + receiverId
        );
    }
    /**
     * 拉取当前用户的离线消息

     * 拉取当前用户尚未确认的消息
     */
    @Override
    public void pullOfflineMessages(
            String userId
    ) {
        if (
                userId == null ||
                        userId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }
        String wsSessionId =
                redisTemplate.opsForValue().get(
                        RedisConstants.USER_WS
                                + userId
                );


        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            log.info(
                    "用户当前不在线，不拉取消息："
                            + userId
            );


            return;
        }


        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + userId;
        List<String> messageJsonList =
                redisTemplate.opsForList().range(
                        offlineKey,
                        0,
                        -1
                );


        if (
                messageJsonList == null ||
                        messageJsonList.isEmpty()
        ) {

            log.info(
                    "待确认消息拉取完成，用户："
                            + userId
                            + "，数量：0"
            );


            return;
        }


        int pushedCount = 0;
        for (String messageJson : messageJsonList) {

            try {

                ChatMessage message =
                        objectMapper.readValue(
                                messageJson,
                                ChatMessage.class
                        );


                /*
                 * 推送消息，收到客户端ACK前仍保留在离线消息列表中
                 */
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/chat",
                        ChatMessageDTO.fromEntity(
                                message
                        )
                );


                pushedCount++;


                log.info(
                        "待确认消息已重新推送，用户："
                                + userId
                                + "，messageId："
                                + message.getId()
                );

            } catch (Exception e) {

                log.info(
                        "待确认消息反序列化失败："
                                + messageJson
                );


                log.info(
                        "失败原因："
                                + e.getMessage()
                );
            }
        }


        log.info(
                "待确认消息拉取完成，用户："
                        + userId
                        + "，数量："
                        + pushedCount
        );
    }
    /**
     * 处理客户端业务ACK
     */
    @Override
    public void handleAck(
            String messageId,
            String receiverId
    ) {
        if (
                messageId == null ||
                        messageId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "messageId不能为空"
            );
        }


        if (
                receiverId == null ||
                        receiverId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "当前确认用户不能为空"
            );
        }


        String ackKey =
                RedisConstants.MSG_ACK
                        + messageId;

        /*
         * 兼容修改前遗留的Hash类型ACK数据，
         * 避免切换为Set后出现WRONGTYPE错误。
         */
        if (
                DataType.HASH.equals(
                        redisTemplate.type(
                                ackKey
                        )
                )
        ) {
            Object oldReceiverObject =
                    redisTemplate.opsForHash()
                            .get(
                                    ackKey,
                                    "receiverId"
                            );

            if (
                    oldReceiverObject != null &&
                            !receiverId.equals(
                                    oldReceiverObject.toString()
                            )
            ) {
                throw new IllegalArgumentException(
                        "无权确认这条消息"
                );
            }

            Object oldStatusObject =
                    redisTemplate.opsForHash()
                            .get(
                                    ackKey,
                                    "status"
                            );

            redisTemplate.delete(
                    ackKey
            );

            if (
                    oldReceiverObject != null &&
                            "ACKED".equals(
                                    String.valueOf(
                                            oldStatusObject
                                    )
                            )
            ) {
                redisTemplate.opsForSet()
                        .add(
                                ackKey,
                                receiverId
                        );
                return;
            }
        }
        Boolean alreadyAcked =
                redisTemplate.opsForSet()
                        .isMember(
                                ackKey,
                                receiverId
                        );


        if (Boolean.TRUE.equals(alreadyAcked)) {
            return;
        }


        /*
         * 只在当前接收者自己的待确认列表中查找消息，
         * 防止确认其他用户的消息。
         */
        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + receiverId;

        List<String> messageJsonList =
                redisTemplate.opsForList()
                        .range(
                                offlineKey,
                                0,
                                -1
                        );

        String matchedMessageJson = null;


        if (messageJsonList != null) {
            for (String messageJson : messageJsonList) {
                try {
                    ChatMessage message =
                            objectMapper.readValue(
                                    messageJson,
                                    ChatMessage.class
                            );

                    if (
                            messageId.equals(
                                    message.getId()
                            )
                    ) {
                        matchedMessageJson =
                                messageJson;
                        break;
                    }
                } catch (Exception exception) {
                    log.info(
                            "检查ACK消息时忽略无效JSON："
                                    + exception.getMessage()
                    );
                }
            }
        }


        if (matchedMessageJson == null) {
            throw new IllegalArgumentException(
                    "无权确认这条消息或消息不存在"
            );
        }


        Long removedCount =
                redisTemplate.opsForList()
                        .remove(
                                offlineKey,
                                1,
                                matchedMessageJson
                        );

        /*
         * 文档规定msg:ack:{messageId}使用Set，
         * Set成员记录已经确认该消息的接收者。
         */
        redisTemplate.opsForSet()
                .add(
                        ackKey,
                        receiverId
                );


        log.info(
                "ACK后删除待确认消息，messageId："
                        + messageId
                        + "，删除数量："
                        + removedCount
        );


        log.info(
                "客户端ACK处理完成，messageId："
                        + messageId
                        + "，接收者："
                        + receiverId
        );
    }
    @Override
    @Transactional
    public void endSessionByAgent(
            String sessionId,
            String agentId
    ) {

        if (
                sessionId == null ||
                        sessionId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }


        ChatSession session =
                chatSessionMapper.selectById(
                        sessionId
                );


        if (session == null) {

            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }


        boolean assignedAgent =
                agentId.equals(
                        session.getAgentId()
                );


        if (!assignedAgent) {

            throw new IllegalArgumentException(
                    "当前客服不是该会话分配的客服"
            );
        }


        boolean finalized =
                finalizeSession(
                        session,
                        agentId
                );


        if (!finalized) {

            return;
        }


        notifySessionClosed(

                session.getUserId(),

                sessionId,

                "MANUAL_END"
        );


        notifySessionClosed(

                session.getAgentId(),

                sessionId,

                "MANUAL_END"
        );


        dequeueAndReassign(
                session.getAgentId()
        );
    }

    @Override
    @Transactional
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

        String operationLockToken = acquireSessionOperationLock(sessionId);
        if (operationLockToken == null) {
            throw new IllegalStateException("会话正在转接或结束，请稍后重试");
        }

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

        Long transferred = redisTemplate.execute(
                TRANSFER_SESSION_REDIS_SCRIPT,
                List.of(
                        RedisConstants.agentSessionsKey(sourceAgentId),
                        RedisConstants.agentSessionsKey(targetAgentId),
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.SESSION_META + sessionId
                ),
                sessionId,
                sourceAgentId,
                targetAgentId,
                String.valueOf(RedisConstants.AGENT_MAX_CONCURRENCY)
        );
        if (!Long.valueOf(1L).equals(transferred)) {
            throw new IllegalArgumentException("目标客服不在线或已达到最大接待数量");
        }

        try {
            int updatedRows = chatSessionMapper.transferSession(
                    sessionId,
                    sourceAgentId,
                    targetAgentId
            );
            if (updatedRows != 1) {
                throw new IllegalStateException("会话归属已变化，转接失败");
            }
        } catch (RuntimeException exception) {
            rollbackTransferredSessionInRedis(
                    sessionId,
                    sourceAgentId,
                    targetAgentId
            );
            throw exception;
        }

        session.setAgentId(targetAgentId);
        notifySessionTransferred(session, sourceAgentId, targetAgentId);
        log.info(
                "会话转接成功，sessionId={}，sourceAgentId={}，targetAgentId={}",
                sessionId,
                sourceAgentId,
                targetAgentId
        );
        } finally {
            releaseSessionOperationLock(sessionId, operationLockToken);
        }
    }

    private void rollbackTransferredSessionInRedis(
            String sessionId,
            String sourceAgentId,
            String targetAgentId
    ) {
        redisTemplate.execute(
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

    private void notifySessionClosed(
            String receiverId,
            String sessionId,
            String reason
    ) {

        if (
                receiverId == null ||
                        receiverId.isBlank()
        ) {

            return;
        }


        ChatSessionDTO notice =
                new ChatSessionDTO();
        notice.setEvent(
                ChatConstants.EVENT_SESSION_CLOSED
        );
        notice.setSessionId(
                sessionId
        );
        notice.setStatus(
                RedisConstants
                        .SESSION_STATUS_CLOSED
        );
        notice.setReason(
                reason
        );


        messagingTemplate.convertAndSendToUser(

                receiverId,

                "/queue/chat",

                notice
        );
    }
    private void dequeueAndReassign(
            String agentId
    ) {

        if (
                agentId == null ||
                        agentId.isBlank()
        ) {

            return;
        }


        Double currentLoad =
                redisTemplate.opsForZSet()
                        .score(
                                RedisConstants.AGENT_LOAD,
                                agentId
                        );


        /*
         * 客服仍在线并且当前有容量时，
         * 才处理等待队列。
         */
        if (
                currentLoad != null &&
                        currentLoad
                                < RedisConstants
                                .AGENT_MAX_CONCURRENCY
        ) {

            fillAvailableAgentCapacity(
                    agentId
            );
        }
    }
    /**
     * 通知用户和客服，会话已经结束
     */
    @Override
    public void notifySessionEnded(
            ChatSession session,
            String operatorId
    ) {
        ChatSessionDTO notice =
                ChatSessionDTO.fromEntity(
                        session,
                        ChatConstants.EVENT_SESSION_ENDED
                );
        notice.setEndedBy(
                operatorId
        );


        /*
         * 通知用户
         */
        messagingTemplate.convertAndSendToUser(
                session.getUserId(),
                "/queue/chat",
                notice
        );


        /*
         * 通知客服
         */
        messagingTemplate.convertAndSendToUser(
                session.getAgentId(),
                "/queue/chat",
                notice
        );


        log.info(
                "会话结束通知已发送，用户："
                        + session.getUserId()
                        + "，客服："
                        + session.getAgentId()
        );
    }
    /**
     * 为客服分配等待队列中的下一位用户
     */
    @Override
    public void processNextWaitingUser(
            String agentId
    ) {
        if (
                agentId == null ||
                        agentId.isBlank()
        ) {

            log.info(
                    "处理等待队列失败：agentId为空"
            );

            return;
        }
        if (!reserveSpecificAgent(agentId)) {
            log.info(
                    "客服已经离线或正在处理会话，不处理等待队列："
                            + agentId
            );
            return;
        }
        while (true) {

            String waitingUserId =
                    redisTemplate.execute(
                            RESERVE_AGENT_AND_DEQUEUE_SCRIPT,
                            List.of(
                                    RedisConstants.AGENT_LOAD,
                                    RedisConstants.QUEUE_PENDING,
                                    RedisConstants.ASSIGNMENT_PENDING,
                                    RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                                    RedisConstants.QUEUE_ENQUEUED_AT,
                                    RedisConstants.QUEUE_VIP_LEVEL,
                                    RedisConstants.AGENT_SKILL_VIP
                            ),
                            agentId,
                            String.valueOf(RedisConstants.AGENT_MAX_CONCURRENCY),
                            String.valueOf(System.currentTimeMillis()),
                            String.valueOf(vipReservedSlots)
                    );


            if (waitingUserId == null) {
                refreshWaitingPositions();
                log.info(
                        "当前没有等待用户"
                );
                return;
            }


            String assignmentLockToken =
                    acquireAssignmentLock(
                            waitingUserId
                    );


            if (assignmentLockToken == null) {
                rollbackAssignmentReservation(waitingUserId, agentId, true);
                return;
            }


            try {
                ChatSession oldSession =
                        chatSessionMapper
                                .findActiveByUserId(
                                        waitingUserId
                                );


                if (oldSession != null) {
                    rollbackAssignmentReservation(waitingUserId, agentId, false);
                    log.info(
                            "跳过已经有活动会话的用户："
                                    + waitingUserId
                    );
                    continue;
                }


                ChatSession newSession;


                try {
                    newSession =
                            createSession(
                                    waitingUserId,
                                    agentId
                            );
                } catch (RuntimeException exception) {
                    handleSessionCreationFailure(waitingUserId, agentId, true);
                    throw exception;
                }

                notifyBothParties(
                        newSession
                );


                log.info(
                        "等待用户分配成功，用户："
                                + waitingUserId
                                + "，客服："
                                + agentId
                                + "，会话："
                                + newSession.getId()
                );
                return;

            } finally {
                releaseAssignmentLock(
                        waitingUserId,
                        assignmentLockToken
                );
                refreshWaitingPositions();
            }
        }
    }
    /**
     * 查询指定会话的聊天历史
     */
    private void rollbackAssignmentReservation(
            String userId,
            String agentId,
            boolean requeue
    ) {
        redisTemplate.execute(
                ROLLBACK_ASSIGNMENT_SCRIPT,
                List.of(
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.QUEUE_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                        RedisConstants.QUEUE_ENQUEUED_AT
                ),
                userId,
                agentId,
                requeue ? "1" : "0"
        );
    }

    @Override
    public void setAgentVipSkill(
            String agentId,
            boolean enabled
    ) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId不能为空");
        }
        SysUser agent = sysUserMapper.selectById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("客服不存在");
        }
        Set<String> roleCodes = sysUserRoleMapper.findRoleCodesByUserId(agentId);
        if (roleCodes == null || !roleCodes.contains("AGENT")) {
            throw new IllegalArgumentException("该用户不具有AGENT角色");
        }
        if (enabled) {
            redisTemplate.opsForSet().add(
                    RedisConstants.AGENT_SKILL_VIP,
                    agentId
            );
        } else {
            redisTemplate.opsForSet().remove(
                    RedisConstants.AGENT_SKILL_VIP,
                    agentId
            );
        }
    }

    @Override
    public Set<String> findVipSkillAgentIds() {
        Set<String> agentIds = redisTemplate.opsForSet().members(
                RedisConstants.AGENT_SKILL_VIP
        );
        return agentIds == null ? Set.of() : agentIds;
    }

    private void clearAssignmentReservation(String userId) {
        redisTemplate.execute(
                CLEAR_ASSIGNMENT_PENDING_SCRIPT,
                List.of(
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD
                ),
                userId
        );
    }

    /**
     * MySQL 已创建会话但 Redis 写入失败时保留 pending，
     * 由对账任务根据数据库活动会话重建 Redis；仅在数据库确实没有会话时回滚预留。
     */
    private void handleSessionCreationFailure(
            String userId,
            String agentId,
            boolean requeueWhenNotCreated
    ) {
        try {
            ChatSession activeSession =
                    chatSessionMapper.findActiveByUserId(userId);
            if (activeSession != null
                    && agentId.equals(activeSession.getAgentId())) {
                log.warn(
                        "会话已写入MySQL但Redis状态尚未确认，将由对账任务修复，sessionId={}",
                        activeSession.getId()
                );
                return;
            }
            rollbackAssignmentReservation(
                    userId,
                    agentId,
                    requeueWhenNotCreated
            );
        } catch (Exception reconciliationException) {
            log.error(
                    "确认会话创建状态失败，保留分配pending等待后续对账，userId={}",
                    userId,
                    reconciliationException
            );
        }
    }

    /**
     * 对应用异常退出后遗留的待确认分配进行最终收敛。
     */
    @Override
    public void reconcilePendingAssignments() {
        Set<String> userIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.ASSIGNMENT_PENDING,
                0,
                System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60),
                0,
                100
        );
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            Object rawPayload = redisTemplate.opsForHash().get(
                    RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                    userId
            );
            if (rawPayload == null) {
                redisTemplate.opsForZSet().remove(
                        RedisConstants.ASSIGNMENT_PENDING,
                        userId
                );
                continue;
            }
            String payload = String.valueOf(rawPayload);
            int separatorIndex = payload.indexOf('|');
            if (separatorIndex <= 0) {
                clearAssignmentReservation(userId);
                continue;
            }
            String reservedAgentId = payload.substring(0, separatorIndex);
            ChatSession activeSession =
                    chatSessionMapper.findActiveByUserId(userId);
            if (activeSession == null) {
                rollbackAssignmentReservation(userId, reservedAgentId, true);
            } else if (reservedAgentId.equals(activeSession.getAgentId())) {
                cacheActiveSessionState(activeSession);
                synchronizeAgentLoad(reservedAgentId);
                clearAssignmentReservation(userId);
            } else {
                rollbackAssignmentReservation(userId, reservedAgentId, false);
            }
        }
        refreshWaitingPositions();
    }

    @Override
    public void reconcileActiveSessionState() {
        List<ChatSession> activeSessions = chatSessionMapper.selectList(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_ACTIVE)
        );

        Map<String, Long> activeCountByAgent = new HashMap<>();
        for (ChatSession session : activeSessions) {
            String cachedAgentId = redisTemplate.opsForValue().get(
                    RedisConstants.SESSION_AGENT + session.getId()
            );
            if (cachedAgentId != null
                    && !cachedAgentId.equals(session.getAgentId())) {
                redisTemplate.opsForSet().remove(
                        RedisConstants.agentSessionsKey(cachedAgentId),
                        session.getId()
                );
            }
            cacheActiveSessionState(session);
            recordVipWaitTime(session);
            activeCountByAgent.merge(session.getAgentId(), 1L, Long::sum);
        }

        Set<String> onlineAgentIds = redisTemplate.opsForZSet().range(
                RedisConstants.AGENT_LOAD,
                0,
                -1
        );
        if (onlineAgentIds == null) {
            return;
        }
        for (String agentId : onlineAgentIds) {
            redisTemplate.opsForZSet().add(
                    RedisConstants.AGENT_LOAD,
                    agentId,
                    activeCountByAgent.getOrDefault(agentId, 0L)
            );
        }
    }

    private void synchronizeAgentLoad(String agentId) {
        Long activeSessionCount = chatSessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getAgentId, agentId)
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_ACTIVE)
        );
        redisTemplate.opsForZSet().add(
                RedisConstants.AGENT_LOAD,
                agentId,
                activeSessionCount == null ? 0 : activeSessionCount
        );
    }

    /**
     * MySQL 已结束但 Redis 尚未收尾，或结束流程在数据库更新前异常退出时，
     * 根据持久化 pending 继续完成数据库与 Redis 的最终收敛。
     */
    @Override
    public void reconcilePendingSessionFinalizations() {
        Set<String> sessionIds = redisTemplate.opsForZSet().rangeByScore(
                RedisConstants.SESSION_FINALIZE_PENDING,
                0,
                System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10),
                0,
                100
        );
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        for (String sessionId : sessionIds) {
            try {
                Object rawPayload = redisTemplate.opsForHash().get(
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD,
                        sessionId
                );
                if (rawPayload == null) {
                    clearSessionFinalizePending(sessionId);
                    continue;
                }
                String[] payloadParts =
                        String.valueOf(rawPayload).split("\\|", 3);
                if (payloadParts.length != 3) {
                    clearSessionFinalizePending(sessionId);
                    continue;
                }
                LocalDateTime requestedEndTime =
                        LocalDateTime.parse(payloadParts[2]);
                ChatSession session =
                        chatSessionMapper.selectById(sessionId);
                if (session == null) {
                    clearSessionFinalizePending(sessionId);
                    continue;
                }
                if (!ChatConstants.SESSION_STATUS_CLOSED.equals(
                        session.getStatus()
                )) {
                    chatSessionMapper.endSession(
                            sessionId,
                            requestedEndTime
                    );
                    session = chatSessionMapper.selectById(sessionId);
                    if (session == null
                            || !ChatConstants.SESSION_STATUS_CLOSED.equals(
                            session.getStatus()
                    )) {
                        continue;
                    }
                }
                LocalDateTime effectiveEndTime =
                        session.getEndTime() == null
                                ? requestedEndTime
                                : session.getEndTime();
                applySessionFinalizationRedis(
                        session,
                        effectiveEndTime,
                        false
                );
            } catch (Exception exception) {
                log.error(
                        "会话结束状态对账失败，sessionId={}",
                        sessionId,
                        exception
                );
            }
        }
    }

    @Override
    public ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            int pageNo,
            int pageSize
    ) {
        if (
                sessionId == null ||
                        sessionId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }


        if (
                operatorId == null ||
                        operatorId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "当前查询用户不能为空"
            );
        }

        if (pageNo < 1) {
            throw new IllegalArgumentException("页码必须大于等于1");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量必须在1到100之间");
        }
        ChatSession session =
                chatSessionMapper.selectById(
                        sessionId
                );


        if (session == null) {
            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }
        boolean isUser =
                operatorId.equals(
                        session.getUserId()
                );


        boolean isAgent =
                operatorId.equals(
                        session.getAgentId()
                );


        if (!isUser && !isAgent) {
            throw new IllegalArgumentException(
                    "当前用户无权查看这个会话的聊天记录"
            );
        }
        Page<ChatMessage> historyPage = chatMessageMapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );


        log.info(
                "聊天历史查询成功，sessionId："
                        + sessionId
                        + "，查询者："
                        + operatorId
                        + "，页码："
                        + pageNo
                        + "，本页消息数量："
                        + historyPage.getRecords().size()
        );


        return new ChatHistoryPage(
                historyPage.getRecords(),
                historyPage.getTotal(),
                historyPage.getCurrent(),
                historyPage.getSize(),
                historyPage.getPages()
        );
    }
    /**
     * 接收客户端心跳并续期在线状态。
     */
    @Override
    public synchronized void handleHeartbeat(
            String userId,
            String wsSessionId
    ) {
        if (
                userId == null ||
                        userId.isBlank() ||
                        wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            return;
        }
        String currentWsSessionId =
                redisTemplate.opsForValue()
                        .get(
                                RedisConstants.USER_WS
                                        + userId
                        );


        if (
                !wsSessionId.equals(
                        currentWsSessionId
                )
        ) {

            log.info(
                    "忽略旧WebSocket连接的心跳，用户："
                            + userId
                            + "，wsSessionId："
                            + wsSessionId
            );

            return;
        }
        refreshOnlineState(
                userId,
                wsSessionId
        );
    }


    @Override
    @Transactional
    public synchronized void handleHeartbeatTimeout(
            String userId
    ) {

        if (
                userId == null ||
                        userId.isBlank()
        ) {

            return;
        }


        /*
         * 定时任务扫描到用户后，
         * 用户可能已经发送了新的心跳。
         * 必须在执行清理前读取最新超时时间。
         *
         * 本方法与handleHeartbeat使用同一对象锁，
         * 避免心跳刷新与超时清理并发执行。
         */
        Double timeoutAt =
                redisTemplate.opsForZSet()
                        .score(
                                RedisConstants
                                        .ONLINE_HEARTBEAT,
                                userId
                        );


        if (
                timeoutAt == null ||
                        timeoutAt
                                > System.currentTimeMillis()
        ) {

            return;
        }


        String wsSessionId =
                redisTemplate.opsForValue()
                        .get(
                                RedisConstants.USER_WS
                                        + userId
                        );


        handleOffline(
                userId,
                wsSessionId,
                ChatConstants.REASON_HEARTBEAT_TIMEOUT
        );
    }
    /**
     * WebSocket断开和心跳超时的统一处理入口。
     */
    private void handleOffline(
            String userId,
            String wsSessionId,
            String reason
    ) {

        /*
         * 必须先判断角色。
         * 清理agent:load以后就无法通过ZSET判断角色了。
         */
        Set<String> roleCodes =
                sysUserRoleMapper
                        .findRoleCodesByUserId(
                                userId
                        );


        boolean isAgent =
                roleCodes != null &&
                        roleCodes.contains(
                                "AGENT"
                        );
        /* Stop new assignments before any session cleanup or reconnect grace handling. */
        if (isAgent) {
            redisTemplate.opsForZSet().remove(
                    RedisConstants.AGENT_LOAD,
                    userId
            );
        }
        cleanupOnlineState(
                userId,
                wsSessionId
        );

        if (isAgent && (ChatConstants.REASON_WEBSOCKET_DISCONNECT.equals(reason)
                || ChatConstants.REASON_HEARTBEAT_TIMEOUT.equals(reason))) {
            scheduleAgentReconnectGrace(userId);
            return;
        }


        /*
         * 如果是仍在排队的普通用户，
         * 断线后必须从等待队列删除。
         */
        redisTemplate.opsForZSet()
                .remove(
                        RedisConstants.QUEUE_PENDING,
                        userId
                );
        redisTemplate.opsForZSet()
                .remove(
                        RedisConstants.QUEUE_ENQUEUED_AT,
                        userId
                );
        redisTemplate.opsForHash()
                .delete(
                        RedisConstants.QUEUE_VIP_LEVEL,
                        userId
                );
        redisTemplate.opsForZSet()
                .remove(
                        RedisConstants.VIP_CALLBACK_PENDING,
                        userId
                );
        if (isAgent) {

            handleAgentDisconnect(
                    userId,
                    reason
            );

        } else {

            handleUserDisconnect(
                    userId,
                    reason
            );
        }
    }

    private void handleUserDisconnect(
            String userId,
            String reason
    ) {

        String sessionId =
                redisTemplate.opsForValue()
                        .get(
                                RedisConstants
                                        .USER_ACTIVE_SESSION
                                        + userId
                        );


        if (
                sessionId == null ||
                        sessionId.isBlank()
        ) {

            return;
        }


        ChatSession session =
                chatSessionMapper.selectById(
                        sessionId
                );


        if (session == null) {

            /*
             * 数据库已经没有会话时，
             * 至少删除残留的活动映射。
             */
            redisTemplate.delete(
                    RedisConstants
                            .USER_ACTIVE_SESSION
                            + userId
            );

            return;
        }


        boolean finalized =
                finalizeSession(
                        session,
                        null
                );


        if (!finalized) {

            return;
        }


        /*
         * 普通用户断开，但客服仍在线，
         * 所以只将客服负载减1。
         */
        notifySessionClosed(

                session.getAgentId(),

                session.getId(),

                reason
        );


        /*
         * 当前客服释放了容量，
         * 尝试接待等待队列中的下一位用户。
         */
        dequeueAndReassign(
                session.getAgentId()
        );
    }

    private void handleAgentDisconnect(
            String agentId,
            String reason
    ) {
        /*
         * 优先通过 Redis 反向索引获取该客服的活动会话。
         * 缓存索引只保存 sessionId，仍需根据 ID 从 MySQL 取得
         * 会话实体，以便执行会话最终结算和持久化状态更新。
         */
        Set<String> indexedSessionIds = redisTemplate.opsForSet().members(
                RedisConstants.agentSessionsKey(agentId)
        );

        List<ChatSession> activeSessions = new ArrayList<>();

        if (indexedSessionIds != null && !indexedSessionIds.isEmpty()) {
            for (String sessionId : indexedSessionIds) {
                ChatSession session = chatSessionMapper.selectById(sessionId);
                if (session != null && ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
                    activeSessions.add(session);
                } else {
                    redisTemplate.opsForSet().remove(
                            RedisConstants.agentSessionsKey(agentId),
                            sessionId
                    );
                }
            }

            activeSessions.sort(
                    Comparator.comparing(ChatSession::getCreateTime)
            );
        }

        /*
         * Redis 索引不存在、丢失或全为失效数据时，才查询 MySQL 兜底，
         * 并在下方重新补齐 Redis 反向索引。
         */
        if (activeSessions.isEmpty()) {
            activeSessions = chatSessionMapper.selectList(
                    Wrappers.<ChatSession>lambdaQuery()
                            .eq(ChatSession::getAgentId, agentId)
                            .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_ACTIVE)
                            .orderByAsc(ChatSession::getCreateTime)
            );

            for (ChatSession session : activeSessions) {
                redisTemplate.opsForSet().add(
                        RedisConstants.agentSessionsKey(agentId),
                        session.getId()
                );
            }
        }

        for (ChatSession session : activeSessions) {
            if (!finalizeSession(session, agentId)) {
                continue;
            }

            String userId =
                    session.getUserId();

            ChatSessionDTO notice =
                    ChatSessionDTO.fromEntity(
                            session,
                            ChatConstants.REASON_AGENT_DISCONNECTED
                    );
            notice.setReason(
                    reason
            );

            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/chat",
                    notice
            );

            /*
             * 用户仍在线时重新进入分配流程。
             */
            Boolean userOnline =
                    redisTemplate.hasKey(
                            RedisConstants.USER_WS
                                    + userId
                    );

            if (Boolean.TRUE.equals(userOnline)) {
                onUserConnected(
                        userId
                );
            }
        }
        redisTemplate.opsForZSet().remove(
                RedisConstants.AGENT_LOAD,
                agentId
        );
    }

    private String acquireAssignmentLock(
            String userId
    ) {

        String lockToken =
                UUID.randomUUID().toString();

        Boolean acquired =
                redisTemplate.opsForValue()
                        .setIfAbsent(
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

    private String acquireSessionOperationLock(String sessionId) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                RedisConstants.SESSION_OPERATION_LOCK + sessionId,
                lockToken,
                RedisConstants.SESSION_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(acquired) ? lockToken : null;
    }

    private void releaseSessionOperationLock(
            String sessionId,
            String lockToken
    ) {
        if (sessionId == null || lockToken == null) {
            return;
        }
        redisTemplate.execute(
                RELEASE_ASSIGNMENT_LOCK_SCRIPT,
                Collections.singletonList(
                        RedisConstants.SESSION_OPERATION_LOCK + sessionId
                ),
                lockToken
        );
    }


    private void releaseAssignmentLock(
            String userId,
            String lockToken
    ) {

        if (
                userId == null ||
                        lockToken == null
        ) {
            return;
        }

        redisTemplate.execute(
                RELEASE_ASSIGNMENT_LOCK_SCRIPT,
                Collections.singletonList(
                        RedisConstants.CHAT_ASSIGN_LOCK
                                + userId
                ),
                lockToken
        );
    }


    private void fillAvailableAgentCapacity(
            String agentId
    ) {
        for (
                int slot = 0;
                slot < RedisConstants.AGENT_MAX_CONCURRENCY;
                slot++
        ) {
            Long waitingCount =
                    redisTemplate.opsForZSet()
                            .zCard(
                                    RedisConstants.QUEUE_PENDING
                            );

            Double currentLoad =
                    redisTemplate.opsForZSet()
                            .score(
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


    private boolean reserveSpecificAgent(
            String agentId
    ) {

        Double currentLoad = redisTemplate.opsForZSet().score(
                RedisConstants.AGENT_LOAD,
                agentId
        );
        return currentLoad != null
                && currentLoad < RedisConstants.AGENT_MAX_CONCURRENCY;
    }


    private void releaseAgentLoad(
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

            redisTemplate.opsForZSet()
                    .remove(
                            RedisConstants.AGENT_LOAD,
                            agentId
                    );

            return;
        }


        Double currentLoad =
                redisTemplate.opsForZSet()
                        .score(
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


        redisTemplate.opsForZSet()
                .add(
                        RedisConstants.AGENT_LOAD,
                        agentId,
                        newLoad
                );
    }

    private boolean finalizeSession(
            ChatSession session,
            String expectedAgentId
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

    private void markSessionFinalizePending(
            ChatSession session,
            LocalDateTime endTime
    ) {
        String payload = String.join(
                "|",
                session.getUserId(),
                session.getAgentId(),
                endTime.toString()
        );
        redisTemplate.execute(
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

    private void clearSessionFinalizePending(String sessionId) {
        redisTemplate.execute(
                CLEAR_SESSION_FINALIZE_PENDING_SCRIPT,
                List.of(
                        RedisConstants.SESSION_FINALIZE_PENDING,
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD
                ),
                sessionId
        );
    }

    private void applySessionFinalizationRedis(
            ChatSession session,
            LocalDateTime endTime,
            boolean agentDisconnected
    ) {
        String sessionId = session.getId();
        redisTemplate.execute(
                FINALIZE_SESSION_REDIS_SCRIPT,
                List.of(
                        RedisConstants.USER_ACTIVE_SESSION + session.getUserId(),
                        RedisConstants.SESSION_USER + sessionId,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.SESSION_META + sessionId,
                        RedisConstants.agentSessionsKey(session.getAgentId()),
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.SESSION_FINALIZE_PENDING,
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD
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
    private void notifyUserSession(
            ChatSession session
    ) {
        AssignResult notice =
                AssignResult.reconnected(
                        session
                );
        notice.setVipLevel(
                getVipLevel(session.getUserId())
        );


        messagingTemplate.convertAndSendToUser(

                session.getUserId(),

                "/queue/chat",

                notice
        );
    }


    /**
     * 无客服时明确通知用户正在排队。
     */
    private void notifyWaitingUser(
            String userId,
            Long waitingPosition
    ) {
        AssignResult notice =
                createWaitingResult(
                        userId,
                        waitingPosition
                );


        messagingTemplate.convertAndSendToUser(

                userId,

                "/queue/chat",

                notice
        );
    }

    private Long getWaitingPosition(String userId) {
        Long rank = redisTemplate.opsForZSet().rank(
                RedisConstants.QUEUE_PENDING,
                userId
        );
        return rank == null ? null : rank + 1;
    }

    private Long estimateWaitingSeconds(Long waitingPosition) {
        if (waitingPosition == null || waitingPosition <= 0) {
            return null;
        }
        Long onlineAgentCount = redisTemplate.opsForZSet().zCard(
                RedisConstants.AGENT_LOAD
        );
        if (onlineAgentCount == null || onlineAgentCount <= 0) {
            return null;
        }
        long serviceRounds = (waitingPosition + onlineAgentCount - 1)
                / onlineAgentCount;
        return Math.multiplyExact(serviceRounds, averageHandleSeconds);
    }

    @Override
    public void refreshWaitingPositions() {
        Set<String> userIds = redisTemplate.opsForZSet().range(
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
    public void registerOnline(
            String userId,
            String wsSessionId
    ) {

        refreshOnlineState(
                userId,
                wsSessionId
        );

        Set<String> roleCodes = sysUserRoleMapper.findRoleCodesByUserId(userId);
        if (roleCodes != null && roleCodes.contains("AGENT")) {
            Long removed = redisTemplate.opsForZSet().remove(
                    RedisConstants.AGENT_RECONNECT_GRACE,
                    userId
            );
            if (removed != null && removed > 0) {
                agentOnline(userId);
            }
        }
    }

    @Override
    public void handleAgentReconnectGraceTimeout(String agentId) {
        Double deadline = redisTemplate.opsForZSet().score(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId
        );
        if (deadline == null || deadline > System.currentTimeMillis()) {
            return;
        }
        redisTemplate.opsForZSet().remove(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId
        );
        handleOffline(agentId, null, "AGENT_RECONNECT_TIMEOUT");
    }

    private void scheduleAgentReconnectGrace(String agentId) {
        redisTemplate.opsForZSet().add(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId,
                System.currentTimeMillis() + agentReconnectGraceMillis
        );
    }




    private void refreshOnlineState(
            String userId,
            String wsSessionId
    ) {

        long nowMillis =
                System.currentTimeMillis();


        long timeoutAt =
                nowMillis
                        + RedisConstants
                        .HEARTBEAT_TIMEOUT_MILLIS;


        String userOnlineKey =
                RedisConstants.USER_ONLINE
                        + userId;


        String userWsKey =
                RedisConstants.USER_WS
                        + userId;


        String wsSessionKey =
                RedisConstants.WS_SESSION
                        + wsSessionId;
        /*
         * 查询用户之前保存的WebSocket连接。
         */
        String previousWsSessionId =
                redisTemplate.opsForValue()
                        .get(
                                userWsKey
                        );


        /*
         * 用户建立了新连接时，
         * 删除旧连接的反向映射。
         *
         * 防止旧连接以后触发断开事件，
         * 误结束新连接对应的聊天会话。
         */
        if (
                previousWsSessionId != null &&
                        !previousWsSessionId.isBlank() &&
                        !wsSessionId.equals(
                                previousWsSessionId
                        )
        ) {

            redisTemplate.delete(
                    RedisConstants.WS_SESSION
                            + previousWsSessionId
            );
        }
        redisTemplate.opsForHash()
                .put(
                        userOnlineKey,
                        "lastHeartbeat",
                        String.valueOf(
                                nowMillis
                        )
                );


        redisTemplate.opsForHash()
                .put(
                        userOnlineKey,
                        "wsSessionId",
                        wsSessionId
                );

        redisTemplate.opsForHash()
                .put(
                        userOnlineKey,
                        "vipLevel",
                        String.valueOf(getVipLevel(userId))
                );


        /*
         * userId → wsSessionId
         */
        redisTemplate.opsForValue()
                .set(
                        userWsKey,
                        wsSessionId,
                        RedisConstants
                                .ONLINE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );


        /*
         * wsSessionId → userId
         */
        redisTemplate.opsForValue()
                .set(
                        wsSessionKey,
                        userId,
                        RedisConstants
                                .ONLINE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );


        /*
         * Hash也必须单独设置TTL。
         */
        redisTemplate.expire(
                userOnlineKey,
                RedisConstants
                        .ONLINE_TTL_SECONDS,
                TimeUnit.SECONDS
        );


        /*
         * 记录全局在线用户。
         */
        redisTemplate.opsForSet()
                .add(
                        RedisConstants.ONLINE_USERS,
                        userId
                );


        /*
         * 当前定时器查询score <= 当前时间，
         * 所以这里保存的是超时截止时间。
         */
        redisTemplate.opsForZSet()
                .add(
                        RedisConstants.ONLINE_HEARTBEAT,
                        userId,
                        timeoutAt
                );
    }
    /**
     * 清理用户或客服的在线状态。
     */
    private void cleanupOnlineState(
            String userId,
            String wsSessionId
    ) {

        redisTemplate.delete(
                RedisConstants.USER_ONLINE
                        + userId
        );


        redisTemplate.delete(
                RedisConstants.USER_WS
                        + userId
        );


        if (
                wsSessionId != null &&
                        !wsSessionId.isBlank()
        ) {

            redisTemplate.delete(
                    RedisConstants.WS_SESSION
                            + wsSessionId
            );
        }


        redisTemplate.opsForSet()
                .remove(
                        RedisConstants.ONLINE_USERS,
                        userId
                );


        redisTemplate.opsForZSet()
                .remove(
                        RedisConstants.ONLINE_HEARTBEAT,
                        userId
                );
    }

    private int getVipLevel(String userId) {
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

    private AssignResult withVipLevel(
            AssignResult result,
            int vipLevel
    ) {
        result.setVipLevel(vipLevel);
        return result;
    }

    private AssignResult createWaitingResult(
            String userId,
            Long waitingPosition
    ) {
        int vipLevel = getVipLevel(userId);
        Long onlineAgentCount = redisTemplate.opsForZSet().zCard(
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
            redisTemplate.opsForZSet().add(
                    RedisConstants.VIP_CALLBACK_PENDING,
                    userId,
                    System.currentTimeMillis()
            );
        }
        return withVipLevel(result, vipLevel);
    }

    private void recordVipWaitTime(ChatSession session) {
        String userId = session.getUserId();
        Double enqueuedAt = redisTemplate.opsForZSet().score(
                RedisConstants.QUEUE_ENQUEUED_AT,
                userId
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
        if (enqueuedAt == null || getVipLevel(userId) <= 0) {
            return;
        }
        long waitMillis = Math.max(
                0L,
                System.currentTimeMillis() - enqueuedAt.longValue()
        );
        redisTemplate.opsForZSet().add(
                RedisConstants.STATS_VIP_WAIT,
                session.getId(),
                waitMillis
        );
    }

    private void recordVipResolveTime(
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
        redisTemplate.opsForZSet().add(
                RedisConstants.STATS_VIP_RESOLVE,
                session.getId(),
                resolveMillis
        );
    }
}
