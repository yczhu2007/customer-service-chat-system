package com.example.customerservice.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.constant.ChatMessageType;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.dto.ChatSessionDTO;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.IChatService;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Service
public class ChatServiceImpl implements IChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ChatServiceImpl.class
            );

    private static final DefaultRedisScript<String>
            RESERVE_IDLE_AGENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local maxLoad = tonumber(ARGV[1]); " +
                    "local agents = redis.call('ZRANGEBYSCORE', KEYS[1], 0, maxLoad - 1, 'LIMIT', 0, 1); " +
                            "if #agents == 0 then return nil; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, agents[1]); " +
                            "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); " +
                            "redis.call('HSET', KEYS[3], ARGV[2], agents[1] .. '|' .. ARGV[3]); " +
                            "return agents[1];",
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
                            "local users = redis.call('ZPOPMIN', KEYS[2], 1); " +
                            "if #users == 0 then return nil; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[3], ARGV[3], users[1]); " +
                            "redis.call('HSET', KEYS[4], users[1], ARGV[1] .. '|' .. users[2]); " +
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
                            "local storedAgent = string.sub(payload, 1, split - 1); " +
                            "local queueScore = tonumber(string.sub(payload, split + 1)); " +
                            "if storedAgent ~= ARGV[2] then return 0; end; " +
                            "if ARGV[3] == '1' then redis.call('ZADD', KEYS[2], queueScore, ARGV[1]); end; " +
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
     * 入队时间仍作为 score 的主体；同一毫秒内通过极小增量保持严格 FIFO。
     * 这样 QueueTimeoutSweeper 可以继续直接按毫秒时间范围清理超时用户。
     */
    private static final DefaultRedisScript<Long>
            ENQUEUE_WAITING_USER_SCRIPT =
            new DefaultRedisScript<>(
                    "local now = tonumber(ARGV[2]); " +
                            "local last = tonumber(redis.call('GET', KEYS[2]) or '0'); " +
                            "local score = now; " +
                            "if last >= score then score = last + 0.001; end; " +
                            "redis.call('SET', KEYS[2], tostring(score)); " +
                            "redis.call('ZADD', KEYS[1], score, ARGV[1]); " +
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

    private final StringRedisTemplate redisTemplate;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessagePersistService messagePersistService;
    private final ObjectMapper objectMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final long agentReconnectGraceMillis;

    public ChatServiceImpl(
            StringRedisTemplate redisTemplate,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            SysUserRoleMapper sysUserRoleMapper,
            @Value("${app.chat.agent-reconnect-grace-seconds:20}")
            long agentReconnectGraceSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.messagingTemplate = messagingTemplate;
        this.messagePersistService = messagePersistService;
        this.objectMapper = objectMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.agentReconnectGraceMillis = agentReconnectGraceSeconds * 1000L;
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

        String assignmentLockToken =
                acquireAssignmentLock(
                        userId
                );


        if (assignmentLockToken == null) {
            LOGGER.info(
                    "用户分配正在处理中，忽略重复请求，userId："
                            + userId
            );
            return AssignResult.processing();
        }


        try {

        /*
         * 1. 优先从Redis检查已有活跃会话。
         */
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
                            "ACTIVE".equals(
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


                return AssignResult.reconnected(
                        cachedSession
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
         * 2. Redis没有有效会话时查询MySQL，
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


            return AssignResult.reconnected(
                    oldSession
            );
        }


        /*
         * 3. 查找当前可用客服。
         */
        String agentId =
                findIdleAgent(userId);


        /*
         * 4. 没有客服则进入等待队列。
         */
        if (agentId == null) {

            enqueueWaitingUser(
                    userId
            );

            Long waitingPosition = getWaitingPosition(userId);
            notifyWaitingUser(userId, waitingPosition);

            return AssignResult.waiting(waitingPosition);
        }


        /*
         * 5. 创建会话。
         */
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

        clearAssignmentReservation(userId);

        /*
         * 6. 通知双方。
         */
        notifyBothParties(
                session
        );


        return AssignResult.assigned(
                session
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

        session.setStatus("ACTIVE");

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

        LOGGER.info(
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
         * 1. 用户 → 当前活动会话
         *
         * user:active:session:U990
         * → 聊天会话ID
         */
        redisTemplate.opsForValue().set(
                RedisConstants.USER_ACTIVE_SESSION
                        + userId,
                sessionId
        );


        /*
         * 2. 会话 → 用户
         */
        redisTemplate.opsForValue().set(
                RedisConstants.SESSION_USER
                        + sessionId,
                userId
        );


        /*
         * 3. 会话 → 客服
         */
        redisTemplate.opsForValue().set(
                RedisConstants.SESSION_AGENT
                        + sessionId,
                agentId
        );

        redisTemplate.opsForSet().add(
                RedisConstants.agentSessionsKey(agentId),
                sessionId
        );


        /*
         * 4. 保存会话元数据
         */
        String metaKey =
                RedisConstants.SESSION_META
                        + sessionId;


        Map<String, String> sessionMeta =
                new HashMap<>();


        sessionMeta.put(
                "status",
                session.getStatus()
        );


        if (session.getCreateTime() != null) {

            sessionMeta.put(
                    "createTime",
                    session.getCreateTime().toString()
            );
        }


        redisTemplate.opsForHash().putAll(
                metaKey,
                sessionMeta
        );


        /*
         * 如果之前存在错误的endTime字段，
         * 恢复活动会话时将其删除
         */
        redisTemplate.opsForHash().delete(
                metaKey,
                "endTime"
        );


        LOGGER.info(
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
                if (session != null && "ACTIVE".equals(session.getStatus())) {
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
                            .eq(ChatSession::getStatus, "ACTIVE")
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

        LOGGER.info(
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


        String wsSessionId =
                redisTemplate.opsForValue()
                        .get(
                                RedisConstants.USER_WS
                                        + agentId
                        );


        /*
         * 客服主动下线与WebSocket断开使用同一套清理流程：
         * 清理在线状态、移出客服负载、结算活动会话并重新分配用户。
         */
        handleOffline(
                agentId,
                wsSessionId,
                "AGENT_OFFLINE"
        );
    }
    @Override
    public String findIdleAgent(String userId) {

        /*
         * 原子查询并占用负载等于0的客服。
         *
         * score = 0：
         * 当前没有活动会话，可以接用户。
         *
         * score >= 1：
         * 已经在处理会话，不能继续分配。
         */
        return redisTemplate.execute(
                RESERVE_IDLE_AGENT_SCRIPT,
                List.of(
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD
                ),
                String.valueOf(
                        RedisConstants
                                .AGENT_MAX_CONCURRENCY
                ),
                userId,
                String.valueOf(System.currentTimeMillis())
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
                        RedisConstants.QUEUE_SEQUENCE
                ),
                userId,
                String.valueOf(System.currentTimeMillis())
        );
        refreshWaitingPositions();
    }
    @Override
    public void notifyBothParties(ChatSession session) {
        AssignResult notice =
                AssignResult.assigned(
                        session
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


        LOGGER.info(
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


        /*
         * 1. 根据断开的WebSocket连接查询用户。
         */
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


        /*
         * 2. 查询该用户当前最新的WebSocket连接。
         */
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


            LOGGER.info(
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
         * 3. 当前连接真正断开，
         * 执行完整业务清理。
         */
        handleOffline(
                userId,
                wsSessionId,
                "WEBSOCKET_DISCONNECT"
        );
    }
    /**
     * 处理聊天消息
     */
    @Override
    public int handleMessage(ChatMessage message) {

        /*
         * 1. 检查必要参数
         */
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

        /*
         * 2. 根据clientMsgId进行消息去重
         */
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

            LOGGER.info(
                    "检测到重复消息，clientMsgId："
                            + message.getClientMsgId()
            );

            return 0;
        }


        /*
         * 3. 查询消息所属会话
         */
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


        if (!"ACTIVE".equals(session.getStatus())) {

            redisTemplate.delete(dedupKey);

            throw new IllegalArgumentException(
                    "聊天会话已经结束"
            );
        }


        /*
         * 4. 判断发送者是不是会话参与者，
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


        /*
         * 5. 生成服务器消息ID和时间
         */
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


        /*
         * 6. 写入Redis热缓存
         */
        cacheMessage(message);


        ChatMessageDTO receivedAcknowledgement =
                ChatMessageDTO.fromEntity(message);
        receivedAcknowledgement.setAckStatus("RECEIVED");
        messagingTemplate.convertAndSendToUser(
                message.getSenderId(),
                "/queue/chat",
                receivedAcknowledgement
        );


        /*
         * 7. 路由并推送给接收方
         */
        routeAndPush(message);


        /*
         * 8. 异步写入MySQL
         */
        messagePersistService.persistMessageAsync(
                message
        );


        LOGGER.info(
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


            LOGGER.info(
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

        /*
         * 1. 查询聊天会话
         */
        ChatSession session =
                chatSessionMapper.selectById(
                        message.getSessionId()
                );


        if (session == null) {

            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }


        /*
         * 2. 确定消息接收者
         */
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
         * 3. 消息先进入待确认列表
         *
         * 只有收到客户端ACK后才删除。
         */
        cacheOfflineMessage(
                receiverId,
                message
        );


        /*
         * 4. 查询接收者是否在线
         */
        String wsSessionId =
                redisTemplate.opsForValue().get(
                        RedisConstants.USER_WS
                                + receiverId
                );


        /*
         * 5. 接收者离线
         *
         * 消息已经保存在Redis中，
         * 等待用户上线后拉取。
         */
        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            LOGGER.info(
                    "接收者当前离线，消息等待上线拉取："
                            + receiverId
            );


            return;
        }


        /*
         * 6. 通过STOMP实时推送
         */
        messagingTemplate.convertAndSendToUser(
                receiverId,
                "/queue/chat",
                ChatMessageDTO.fromEntity(
                        message
                )
        );


        LOGGER.info(
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

        /*
         * 1. 检查参数
         */
        if (
                userId == null ||
                        userId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }


        /*
         * 2. 确认用户当前在线
         */
        String wsSessionId =
                redisTemplate.opsForValue().get(
                        RedisConstants.USER_WS
                                + userId
                );


        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            LOGGER.info(
                    "用户当前不在线，不拉取消息："
                            + userId
            );


            return;
        }


        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + userId;


        /*
         * 3. 读取消息，但不要删除
         */
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

            LOGGER.info(
                    "待确认消息拉取完成，用户："
                            + userId
                            + "，数量：0"
            );


            return;
        }


        int pushedCount = 0;


        /*
         * 4. 按列表顺序逐条推送
         */
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


                LOGGER.info(
                        "待确认消息已重新推送，用户："
                                + userId
                                + "，messageId："
                                + message.getId()
                );

            } catch (Exception e) {

                LOGGER.info(
                        "待确认消息反序列化失败："
                                + messageJson
                );


                LOGGER.info(
                        "失败原因："
                                + e.getMessage()
                );
            }
        }


        LOGGER.info(
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

        /*
         * 1. 参数检查
         */
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


        /*
         * 2. Set中已经存在当前接收者，说明这条消息已经确认过。
         */
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
         * 3. 只在当前接收者自己的待确认列表中查找消息，
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
                    LOGGER.info(
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


        LOGGER.info(
                "ACK后删除待确认消息，messageId："
                        + messageId
                        + "，删除数量："
                        + removedCount
        );


        LOGGER.info(
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
                        session
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
                "SESSION_CLOSED"
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
                        "SESSION_ENDED"
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


        LOGGER.info(
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

        /*
         * 1. 检查客服ID
         */
        if (
                agentId == null ||
                        agentId.isBlank()
        ) {

            LOGGER.info(
                    "处理等待队列失败：agentId为空"
            );

            return;
        }


        /*
         * 2. 只有仍在线且负载为0的客服才能被原子占用。
         */
        if (!reserveSpecificAgent(agentId)) {
            LOGGER.info(
                    "客服已经离线或正在处理会话，不处理等待队列："
                            + agentId
            );
            return;
        }


        /*
         * 3. 从队列左边取出最早等待的用户。
         */
        while (true) {

            String waitingUserId =
                    redisTemplate.execute(
                            RESERVE_AGENT_AND_DEQUEUE_SCRIPT,
                            List.of(
                                    RedisConstants.AGENT_LOAD,
                                    RedisConstants.QUEUE_PENDING,
                                    RedisConstants.ASSIGNMENT_PENDING,
                                    RedisConstants.ASSIGNMENT_PENDING_PAYLOAD
                            ),
                            agentId,
                            String.valueOf(RedisConstants.AGENT_MAX_CONCURRENCY),
                            String.valueOf(System.currentTimeMillis())
                    );


            if (waitingUserId == null) {
                refreshWaitingPositions();
                LOGGER.info(
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
                /*
                 * 4. 防止队列旧数据导致重复创建会话。
                 */
                ChatSession oldSession =
                        chatSessionMapper
                                .findActiveByUserId(
                                        waitingUserId
                                );


                if (oldSession != null) {
                    rollbackAssignmentReservation(waitingUserId, agentId, false);
                    LOGGER.info(
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

                clearAssignmentReservation(waitingUserId);

                notifyBothParties(
                        newSession
                );


                LOGGER.info(
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
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD
                ),
                userId,
                agentId,
                requeue ? "1" : "0"
        );
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
                LOGGER.warn(
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
            LOGGER.error(
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

    private void synchronizeAgentLoad(String agentId) {
        Long activeSessionCount = chatSessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getAgentId, agentId)
                        .eq(ChatSession::getStatus, "ACTIVE")
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
                if (!RedisConstants.SESSION_STATUS_CLOSED.equals(
                        session.getStatus()
                )) {
                    chatSessionMapper.endSession(
                            sessionId,
                            requestedEndTime
                    );
                    session = chatSessionMapper.selectById(sessionId);
                    if (session == null
                            || !RedisConstants.SESSION_STATUS_CLOSED.equals(
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
                LOGGER.error(
                        "会话结束状态对账失败，sessionId={}",
                        sessionId,
                        exception
                );
            }
        }
    }

    @Override
    public List<ChatMessage> getHistory(
            String sessionId,
            String operatorId
    ) {

        /*
         * 1. 检查参数
         */
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


        /*
         * 2. 查询会话
         */
        ChatSession session =
                chatSessionMapper.selectById(
                        sessionId
                );


        if (session == null) {
            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }


        /*
         * 3. 检查当前查询者是否属于该会话
         */
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


        /*
         * 4. 从MySQL查询完整聊天记录
         */
        List<ChatMessage> messages =
                chatMessageMapper.findBySessionId(
                        sessionId
                );


        LOGGER.info(
                "聊天历史查询成功，sessionId："
                        + sessionId
                        + "，查询者："
                        + operatorId
                        + "，消息数量："
                        + messages.size()
        );


        return messages;
    }
    /**
     * 接收客户端心跳并续期在线状态。
     */
    @Override
    public synchronized void handleHeartbeat(
            String userId,
            String wsSessionId
    ) {

        /*
         * 1. 参数检查。
         */
        if (
                userId == null ||
                        userId.isBlank() ||
                        wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            return;
        }


        /*
         * 2. 查询该用户当前最新的WebSocket连接。
         */
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

            LOGGER.info(
                    "忽略旧WebSocket连接的心跳，用户："
                            + userId
                            + "，wsSessionId："
                            + wsSessionId
            );

            return;
        }


        /*
         * 3. 统一刷新在线状态和TTL。
         */
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
                "HEARTBEAT_TIMEOUT"
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


        /*
         * 1. 清理在线状态。
         */
        cleanupOnlineState(
                userId,
                wsSessionId
        );

        if (isAgent && ("WEBSOCKET_DISCONNECT".equals(reason)
                || "HEARTBEAT_TIMEOUT".equals(reason))) {
            scheduleAgentReconnectGrace(userId);
            return;
        }


        /*
         * 2. 如果是仍在排队的普通用户，
         * 断线后必须从等待队列删除。
         */
        redisTemplate.opsForZSet()
                .remove(
                        RedisConstants.QUEUE_PENDING,
                        userId
                );


        /*
         * 3. 按角色进入不同清理分支。
         */
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
                        session
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
        List<ChatSession> activeSessions =
                chatSessionMapper.selectList(
                        Wrappers.<ChatSession>lambdaQuery()
                                .eq(
                                        ChatSession::getAgentId,
                                        agentId
                                )
                                .eq(
                                        ChatSession::getStatus,
                                        "ACTIVE"
                                )
                                .orderByAsc(
                                        ChatSession::getCreateTime
                                )
                );


        /*
         * 客服已经离线，不应继续参与分配。
         */
        Set<String> indexedSessionIds = redisTemplate.opsForSet().members(
                RedisConstants.agentSessionsKey(agentId)
        );
        if (indexedSessionIds != null && !indexedSessionIds.isEmpty()) {
            activeSessions = indexedSessionIds.stream()
                    .map(chatSessionMapper::selectById)
                    .filter(Objects::nonNull)
                    .filter(session -> "ACTIVE".equals(session.getStatus()))
                    .sorted(Comparator.comparing(ChatSession::getCreateTime))
                    .toList();
        }

        for (ChatSession session : activeSessions) {
            if (!finalizeSession(session)) {
                continue;
            }

            String userId =
                    session.getUserId();

            ChatSessionDTO notice =
                    ChatSessionDTO.fromEntity(
                            session,
                            "AGENT_DISCONNECTED"
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
            ChatSession session
    ) {

        if (session == null) {

            return false;
        }


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
            if (!RedisConstants.SESSION_STATUS_CLOSED.equals(
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
                AssignResult.waiting(waitingPosition);


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
}
