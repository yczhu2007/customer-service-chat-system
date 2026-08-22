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
abstract class ChatRoutingSessionMaintenanceSupport extends ChatRoutingSessionSupport {

    protected ChatRoutingSessionMaintenanceSupport(
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


        afterCommit(() -> {
            notifySessionClosedSafely(session.getUserId(), sessionId, "MANUAL_END");
            notifySessionClosedSafely(session.getAgentId(), sessionId, "MANUAL_END");
            try {
                dequeueAndReassign(session.getAgentId());
            } catch (RuntimeException exception) {
                log.error("会话结束后的等待队列处理失败，sessionId={}", sessionId, exception);
            }
        });
    }

    @Override
    public void transferSession(
            String sessionId,
            String sourceAgentId,
            String targetAgentId
    ) {
        chatSessionTransferOperations.transferSession(
                sessionId, sourceAgentId, targetAgentId
        );
    }
    protected void notifySessionClosed(String userId, String sessionId, String reason) {
        chatSessionNotificationOperations.notifySessionClosed(userId, sessionId, reason);
    }

    private void notifySessionClosedSafely(String userId, String sessionId, String reason) {
        try {
            notifySessionClosed(userId, sessionId, reason);
        } catch (RuntimeException exception) {
            log.error("会话关闭通知发送失败，userId={}，sessionId={}", userId, sessionId, exception);
        }
    }
    protected void dequeueAndReassign(
            String agentId
    ) {

        if (
                agentId == null ||
                        agentId.isBlank()
        ) {

            return;
        }


        Double currentLoad =
                chatRedisRepository.sortedSetScore(
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
    public void notifySessionEnded(ChatSession session, String operatorId) {
        chatSessionNotificationOperations.notifySessionEnded(session, operatorId);
    }
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
                    chatRedisRepository.execute(
                            RESERVE_AGENT_AND_DEQUEUE_SCRIPT,
                            List.of(
                                    RedisConstants.AGENT_LOAD,
                                    RedisConstants.QUEUE_PENDING,
                                    RedisConstants.ASSIGNMENT_PENDING,
                                    RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                                    RedisConstants.QUEUE_ENQUEUED_AT,
                                    RedisConstants.QUEUE_VIP_LEVEL,
                                    RedisConstants.AGENT_SKILL_VIP,
                                    RedisConstants.AGENT_LAST_ASSIGNED
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
    protected void rollbackAssignmentReservation(
            String userId,
            String agentId,
            boolean requeue
    ) {
        chatRedisRepository.execute(
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

    protected void clearAssignmentReservation(String userId) {
        chatRedisRepository.execute(
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
    protected void handleSessionCreationFailure(
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
    public abstract ChatSession createSession(String userId, String agentId);

    public abstract void enqueueWaitingUser(String userId);

    public abstract void notifyBothParties(ChatSession session);

}
