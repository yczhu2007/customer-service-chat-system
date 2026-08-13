package com.example.customerservice.service.impl;

import static com.example.customerservice.service.impl.ChatRedisScripts.*;


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
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Slf4j
public class ChatRoutingSessionService extends ChatRoutingSessionMaintenanceSupport implements IChatService, ChatPresenceCallbacks {

    private final ChatSessionReconciliationService reconciliationService;
    private final ChatSessionInactivityService inactivityService;

    public ChatRoutingSessionService(
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
        super(
                chatRedisRepository, chatMessageOperations, chatPresenceOperations,
                chatSessionTransferOperations, chatSessionNotificationOperations,
                chatSessionMapper, chatMessageMapper, chatMessageReadMapper,
                messagingTemplate, messagePersistService, objectMapper,
                sysUserRoleMapper, sysUserMapper, agentReconnectGraceSeconds,
                vipReservedSlots, averageHandleSeconds, vipPriorityStepSeconds,
                messageRecallWindowSeconds, messageEditWindowSeconds
        );
        this.reconciliationService = new ChatSessionReconciliationService(
                chatRedisRepository,
                chatSessionMapper,
                this
        );
        this.inactivityService = new ChatSessionInactivityService(
                chatRedisRepository,
                chatSessionMapper,
                this
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
                chatRedisRepository.getValue(
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
            chatRedisRepository.delete(
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
    void cacheActiveSessionState(
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

        Long committed = chatRedisRepository.execute(
                COMMIT_ACTIVE_SESSION_SCRIPT,
                List.of(
                        RedisConstants.USER_ACTIVE_SESSION + userId,
                        RedisConstants.SESSION_USER + sessionId,
                        RedisConstants.SESSION_AGENT + sessionId,
                        RedisConstants.agentSessionsKey(agentId),
                        metaKey,
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                        RedisConstants.QUEUE_PENDING,
                        RedisConstants.SESSION_LAST_ACTIVITY
                ),
                sessionId,
                userId,
                agentId,
                session.getStatus(),
                session.getCreateTime() == null
                        ? ""
                        : session.getCreateTime().toString(),
                String.valueOf(getVipLevel(userId)),
                String.valueOf(System.currentTimeMillis())
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

        Set<String> indexedSessionIds = chatRedisRepository.setMembers(
                RedisConstants.agentSessionsKey(agentId)
        );
        List<ChatSession> activeSessions = new ArrayList<>();
        if (indexedSessionIds != null && !indexedSessionIds.isEmpty()) {
            for (String sessionId : indexedSessionIds) {
                ChatSession session = chatSessionMapper.selectById(sessionId);
                if (session != null && ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
                    activeSessions.add(session);
                } else {
                    chatRedisRepository.setRemove(
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
            chatRedisRepository.setAdd(
                    RedisConstants.agentSessionsKey(agentId),
                    activeSession.getId()
            );
        }

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
        chatRedisRepository.sortedSetRemove(
                RedisConstants.AGENT_LOAD,
                agentId
        );
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
    public String findIdleAgent(String userId) {
        return findIdleAgent(userId, null);
    }

    String findIdleAgent(
            String userId,
            String excludedAgentId
    ) {

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
        return chatRedisRepository.execute(
                RESERVE_IDLE_AGENT_SCRIPT,
                List.of(
                        RedisConstants.AGENT_LOAD,
                        RedisConstants.ASSIGNMENT_PENDING,
                        RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                        RedisConstants.AGENT_SKILL_VIP,
                        RedisConstants.AGENT_LAST_ASSIGNED
                ),
                String.valueOf(
                        RedisConstants
                                .AGENT_MAX_CONCURRENCY
                ),
                userId,
                String.valueOf(System.currentTimeMillis()),
                vipLevel > 0 ? "1" : "0",
                String.valueOf(vipReservedSlots),
                excludedAgentId == null ? "" : excludedAgentId
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
        chatRedisRepository.execute(
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
        chatSessionNotificationOperations.notifyBothParties(
                session,
                getVipLevel(session.getUserId())
        );
    }
    @Override
    public void handleDisconnect(String userId) {
        chatPresenceOperations.handleDisconnect(userId);
    }
    @Override
    public int handleMessage(ChatMessage message) {
        return chatMessageOperations.handleMessage(message);
    }

    @Override
    public void cacheMessage(ChatMessage message) {
        chatMessageOperations.cacheMessage(message);
    }

    @Override
    public void routeAndPush(ChatMessage message) {
        chatMessageOperations.routeAndPush(message);
    }

    @Override
    public void pullOfflineMessages(String userId) {
        chatMessageOperations.pullOfflineMessages(userId);
    }

    @Override
    public void handleAck(String messageId, String receiverId) {
        chatMessageOperations.handleAck(messageId, receiverId);
    }

    @Override
    public MessageReadResult markMessagesRead(
            String sessionId,
            String lastReadMessageId,
            String readerId
    ) {
        return chatMessageOperations.markMessagesRead(
                sessionId, lastReadMessageId, readerId
        );
    }

    @Override
    public long countUnreadMessages(String sessionId, String userId) {
        return chatMessageOperations.countUnreadMessages(sessionId, userId);
    }

    @Override
    public MessageMutationResult editMessage(
            String messageId,
            String newContent,
            String operatorId
    ) {
        return chatMessageOperations.editMessage(messageId, newContent, operatorId);
    }

    @Override
    public MessageMutationResult recallMessage(String messageId, String operatorId) {
        return chatMessageOperations.recallMessage(messageId, operatorId);
    }
    @Override
    public void reconcilePendingAssignments() {
        reconciliationService.reconcilePendingAssignments();
    }

    @Override
    public void reconcileActiveSessionState() {
        reconciliationService.reconcileActiveSessionState();
    }


    /**
     * MySQL 已结束但 Redis 尚未收尾，或结束流程在数据库更新前异常退出时，
     * 根据持久化 pending 继续完成数据库与 Redis 的最终收敛。
     */
    @Override
    public void reconcilePendingSessionFinalizations() {
        reconciliationService.reconcilePendingSessionFinalizations();
    }

    @Override
    public void handleSessionInactivityTimeout(
            String sessionId,
            long cutoffMillis
    ) {
        inactivityService.handleSessionInactivityTimeout(sessionId, cutoffMillis);
    }

    @Override
    public ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            String beforeMessageId,
            int pageSize
    ) {
        return chatMessageOperations.getHistory(
                sessionId, operatorId, beforeMessageId, pageSize
        );
    }
    @Override
    public void handleHeartbeat(String userId, String wsSessionId) {
        chatPresenceOperations.handleHeartbeat(userId, wsSessionId);
    }

    @Override
    public void handleHeartbeatTimeout(String userId) {
        chatPresenceOperations.handleHeartbeatTimeout(userId);
    }
}
