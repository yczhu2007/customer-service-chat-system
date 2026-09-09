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
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceCallbacks;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionOperations;
import com.example.customerservice.service.ChatSessionTransferOperations;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
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
abstract class ChatRoutingSessionSupport
        implements ChatRoutingOperations, ChatSessionOperations, ChatPresenceCallbacks {


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
    protected final long antiStarvationMillis;
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
            long antiStarvationSeconds,
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
        this.antiStarvationMillis = Math.max(1L, antiStarvationSeconds) * 1000L;
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
        return chatRedisRepository.acquireSessionOperationLock(sessionId);
    }

    protected void releaseSessionOperationLock(
            String sessionId,
            String lockToken
    ) {
        if (sessionId == null || lockToken == null) {
            return;
        }
        chatRedisRepository.releaseSessionOperationLock(sessionId, lockToken);
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
        try {
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
        } finally {
            refreshWaitingPositions();
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

        String operationLockToken = acquireSessionOperationLock(session.getId());
        if (operationLockToken == null) {
            log.info("Session is being changed; defer finalization to reconciliation: {}", session.getId());
            return false;
        }

        java.util.concurrent.ScheduledFuture<?> operationLockRenewal = chatRedisRepository.startLockRenewal(
                RedisConstants.SESSION_OPERATION_LOCK + session.getId(),
                operationLockToken,
                RedisConstants.SESSION_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );

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
                ChatConstants.SESSION_STATUS_CLOSED
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
        applySessionFinalizationRedisAfterCommit(session, endTime, false);


        return true;
        } finally {
            chatRedisRepository.stopLockRenewal(operationLockRenewal);
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

    protected void applySessionFinalizationRedisAfterCommit(
            ChatSession session,
            LocalDateTime endTime,
            boolean agentDisconnected
    ) {
        afterCommit(() -> applySessionFinalizationRedis(session, endTime, agentDisconnected));
    }

    protected void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                action.run();
                            } catch (RuntimeException exception) {
                                log.error("事务提交后的会话状态处理失败", exception);
                            }
                        }
                    }
            );
            return;
        }
        action.run();
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
        Long onlineAgentCount = chatRedisRepository.sortedSetCardinality(
                RedisConstants.AGENT_LOAD
        );
        return estimateWaitingSeconds(waitingPosition, onlineAgentCount);
    }

    private Long estimateWaitingSeconds(
            Long waitingPosition,
            Long onlineAgentCount
    ) {
        if (waitingPosition == null || waitingPosition <= 0) {
            return null;
        }
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
        List<String> waitingUserIds = new ArrayList<>(userIds);
        List<Object> vipLevels = chatRedisRepository.hashMultiGet(
                RedisConstants.QUEUE_VIP_LEVEL,
                waitingUserIds
        );
        Long onlineAgentCount = chatRedisRepository.sortedSetCardinality(
                RedisConstants.AGENT_LOAD
        );
        for (int index = 0; index < waitingUserIds.size(); index++) {
            String userId = waitingUserIds.get(index);
            Object vipLevel = vipLevels != null && index < vipLevels.size()
                    ? vipLevels.get(index) : null;
            chatSessionNotificationOperations.notifyWaitingUser(
                    userId,
                    createWaitingResult(
                            userId,
                            (long) index + 1,
                            getQueuedVipLevel(userId, vipLevel),
                            onlineAgentCount
                    )
            );
        }
    }
    @Override
    public AssignResult reconnectUser(String userId) {
        return onUserConnected(userId);
    }

    @Override
    public abstract void restoreAgentOnline(String agentId);

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
        return createWaitingResult(
                userId,
                waitingPosition,
                vipLevel,
                onlineAgentCount
        );
    }

    private int getQueuedVipLevel(String userId, Object vipLevel) {
        if (vipLevel == null) {
            return getVipLevel(userId);
        }
        try {
            return Math.max(0, Math.min(Integer.parseInt(vipLevel.toString()), 5));
        } catch (NumberFormatException exception) {
            return getVipLevel(userId);
        }
    }

    private AssignResult createWaitingResult(
            String userId,
            Long waitingPosition,
            int vipLevel,
            Long onlineAgentCount
    ) {
        boolean callbackRequired =
                vipLevel > 0
                        && (onlineAgentCount == null || onlineAgentCount == 0);
        AssignResult result =
                callbackRequired
                        ? AssignResult.vipCallbackRequired(waitingPosition)
                        : AssignResult.waiting(
                                waitingPosition,
                                estimateWaitingSeconds(waitingPosition, onlineAgentCount)
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
        chatRedisRepository.sortedSetAdd(
                RedisConstants.STATS_VIP_WAIT_CREATED_AT,
                session.getId(),
                System.currentTimeMillis()
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
        chatRedisRepository.sortedSetAdd(
                RedisConstants.STATS_VIP_RESOLVE_CREATED_AT,
                session.getId(),
                System.currentTimeMillis()
        );
    }

    public abstract AssignResult onUserConnected(String userId);

    public abstract void processNextWaitingUser(String agentId);

    protected abstract void dequeueAndReassign(String agentId);

    protected abstract void notifySessionClosed(String userId, String sessionId, String reason);
}
