package com.example.customerservice.service.impl;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import lombok.extern.slf4j.Slf4j;

/** Handles inactive-session finalization and reassignment. */
@Slf4j
final class ChatSessionInactivityService {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatRoutingSessionService routingSession;

    ChatSessionInactivityService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatRoutingSessionService routingSession
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.routingSession = routingSession;
    }

    void handleSessionInactivityTimeout(
            String sessionId,
            long cutoffMillis
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null
                || !ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
            chatRedisRepository.sortedSetRemove(
                    RedisConstants.SESSION_LAST_ACTIVITY,
                    sessionId
            );
            return;
        }

        String userId = session.getUserId();
        String sourceAgentId = session.getAgentId();
        String assignmentLockToken = routingSession.acquireAssignmentLock(userId);
        if (assignmentLockToken == null) {
            return;
        }

        String targetAgentId = null;
        try {
            boolean userOnline = Boolean.TRUE.equals(
                    chatRedisRepository.hasKey(RedisConstants.USER_WS + userId)
            );
            if (userOnline) {
                targetAgentId = routingSession.findIdleAgent(userId, sourceAgentId);
                if (targetAgentId == null) {
                    return;
                }
            }

            boolean finalized = routingSession.finalizeSession(
                    session,
                    sourceAgentId,
                    cutoffMillis
            );
            if (!finalized) {
                if (targetAgentId != null) {
                    routingSession.rollbackAssignmentReservation(
                            userId,
                            targetAgentId,
                            false
                    );
                }
                return;
            }

            routingSession.notifySessionClosed(
                    userId,
                    sessionId,
                    ChatConstants.REASON_SESSION_INACTIVITY_TIMEOUT
            );
            routingSession.notifySessionClosed(
                    sourceAgentId,
                    sessionId,
                    ChatConstants.REASON_SESSION_INACTIVITY_TIMEOUT
            );

            if (targetAgentId != null) {
                try {
                    ChatSession reassignedSession = routingSession.createSession(
                            userId,
                            targetAgentId
                    );
                    routingSession.notifyBothParties(reassignedSession);
                    log.info(
                            "会话无活动超时后已转分配，oldSessionId={}，newSessionId={}，sourceAgentId={}，targetAgentId={}",
                            sessionId,
                            reassignedSession.getId(),
                            sourceAgentId,
                            targetAgentId
                    );
                } catch (RuntimeException exception) {
                    routingSession.handleSessionCreationFailure(
                            userId,
                            targetAgentId,
                            false
                    );
                    routingSession.enqueueWaitingUser(userId);
                    routingSession.notifyWaitingUser(userId, routingSession.getWaitingPosition(userId));
                    throw exception;
                }
            }
            routingSession.dequeueAndReassign(sourceAgentId);
        } finally {
            routingSession.releaseAssignmentLock(userId, assignmentLockToken);
        }
    }
}
