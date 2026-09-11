package com.example.customerservice.service.impl;

import static com.example.customerservice.constant.ChatDestinations.USER_CHAT_QUEUE;

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
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatMaintenanceOperations;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceCallbacks;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatSessionTransferOperations;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
public class ChatRoutingSessionService extends ChatRoutingSessionMaintenanceSupport
        implements ChatMaintenanceOperations, ChatPresenceCallbacks {

    @Override
    public void handleInactiveSessions(long cutoffMillis, int batchSize) {
        Set<String> sessionIds = chatRedisRepository.sortedSetRangeByScore(
                RedisConstants.SESSION_LAST_ACTIVITY, 0, cutoffMillis, 0, batchSize);
        if (sessionIds == null) return;
        for (String sessionId : sessionIds) {
            try { handleSessionInactivityTimeout(sessionId, cutoffMillis); }
            catch (Exception exception) { log.error("会话无活动超时转分配失败，sessionId={}", sessionId, exception); }
        }
    }

    private final ChatSessionReconciliationService reconciliationService;
    private final ChatSessionInactivityService inactivityService;
    private final ObjectProvider<ChatAgentOperations> agentOperationsProvider;
    private final QueueTimeoutService queueTimeoutService;

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
            ObjectProvider<ChatAgentOperations> agentOperationsProvider,
            QueueTimeoutService queueTimeoutService,
            long agentReconnectGraceSeconds,
            int vipReservedSlots,
            long averageHandleSeconds,
            long vipPriorityStepSeconds,
            long antiStarvationSeconds,
            long messageRecallWindowSeconds,
            long messageEditWindowSeconds,
            int activeSessionReconciliationBatchSize
    ) {
        super(
                chatRedisRepository, chatMessageOperations, chatPresenceOperations,
                chatSessionTransferOperations, chatSessionNotificationOperations,
                chatSessionMapper, chatMessageMapper, chatMessageReadMapper,
                messagingTemplate, messagePersistService, objectMapper,
                sysUserRoleMapper, sysUserMapper, agentReconnectGraceSeconds,
                vipReservedSlots, averageHandleSeconds, vipPriorityStepSeconds,
                antiStarvationSeconds,
                messageRecallWindowSeconds, messageEditWindowSeconds
        );
        this.reconciliationService = new ChatSessionReconciliationService(
                chatRedisRepository,
                chatSessionMapper,
                this,
                activeSessionReconciliationBatchSize
        );
        this.inactivityService = new ChatSessionInactivityService(
                chatRedisRepository,
                chatSessionMapper,
                this
        );
        this.agentOperationsProvider = agentOperationsProvider;
        this.queueTimeoutService = queueTimeoutService;
    }

    @Override
    public void removeTimedOutWaitingUsers() {
        queueTimeoutService.removeTimedOutUsers(this);
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
                        RedisConstants.SESSION_LAST_ACTIVITY,
                        RedisConstants.QUEUE_NORMAL_DUE
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
    public void restoreAgentOnline(String agentId) {
        agentOperationsProvider.getObject().agentOnline(agentId);
    }
    @Override
    public String findIdleAgent(String userId) {
        return findIdleAgent(userId, userId);
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

        enqueueWaitingUserInternal(userId);
        refreshWaitingPositions();
    }

    @Override
    public boolean cancelWaitingUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId不能为空");
        }
        Long removed = chatRedisRepository.cancelQueueEntry(userId);
        boolean cancelled = removed != null && removed > 0;
        try {
            refreshWaitingPositions();
        } catch (RuntimeException exception) {
            log.warn("刷新排队位置通知失败，取消操作已完成，userId={}", userId, exception);
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    USER_CHAT_QUEUE,
                    Map.of("event", "QUEUE_CANCELLED")
            );
        } catch (RuntimeException exception) {
            log.warn("发送取消排队通知失败，取消操作已完成，userId={}", userId, exception);
        }
        return cancelled;
    }

    @Override
    public void backfillWaitingUsers(Iterable<String> userIds) {
        boolean changed = false;
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            enqueueWaitingUserInternal(userId);
            changed = true;
        }
        if (changed) {
            refreshWaitingPositions();
        }
    }

    private void enqueueWaitingUserInternal(String userId) {

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
                        RedisConstants.QUEUE_VIP_LEVEL,
                        RedisConstants.QUEUE_NORMAL_DUE
                ),
                userId,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(getVipLevel(userId)),
                String.valueOf(vipPriorityStepMillis),
                String.valueOf(antiStarvationMillis)
        );
    }
    @Override
    public void notifyBothParties(ChatSession session) {
        chatSessionNotificationOperations.notifyBothParties(
                session,
                getVipLevel(session.getUserId())
        );
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

}
