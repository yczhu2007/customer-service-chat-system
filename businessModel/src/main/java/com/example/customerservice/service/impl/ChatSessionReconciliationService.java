package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Repairs pending assignments and converges MySQL/Redis session state. */
@Slf4j
final class ChatSessionReconciliationService {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatRoutingSessionService routingSupport;

    ChatSessionReconciliationService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatRoutingSessionService routingSupport
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.routingSupport = routingSupport;
    }

    void reconcilePendingAssignments() {
        Set<String> userIds = chatRedisRepository.sortedSetRangeByScore(
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
            Object rawPayload = chatRedisRepository.hashGet(
                    RedisConstants.ASSIGNMENT_PENDING_PAYLOAD,
                    userId
            );
            if (rawPayload == null) {
                chatRedisRepository.sortedSetRemove(
                        RedisConstants.ASSIGNMENT_PENDING,
                        userId
                );
                continue;
            }
            String payload = String.valueOf(rawPayload);
            int separatorIndex = payload.indexOf('|');
            if (separatorIndex <= 0) {
                routingSupport.clearAssignmentReservation(userId);
                continue;
            }
            String reservedAgentId = payload.substring(0, separatorIndex);
            ChatSession activeSession =
                    chatSessionMapper.findActiveByUserId(userId);
            if (activeSession == null) {
                routingSupport.rollbackAssignmentReservation(userId, reservedAgentId, true);
            } else if (reservedAgentId.equals(activeSession.getAgentId())) {
                routingSupport.cacheActiveSessionState(activeSession);
                synchronizeAgentLoad(reservedAgentId);
                routingSupport.clearAssignmentReservation(userId);
            } else {
                routingSupport.rollbackAssignmentReservation(userId, reservedAgentId, false);
            }
        }
        routingSupport.refreshWaitingPositions();
    }

    void reconcileActiveSessionState() {
        List<ChatSession> activeSessions = chatSessionMapper.selectList(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_ACTIVE)
        );

        Map<String, Long> activeCountByAgent = new HashMap<>();
        for (ChatSession session : activeSessions) {
            String cachedAgentId = chatRedisRepository.getValue(
                    RedisConstants.SESSION_AGENT + session.getId()
            );
            if (cachedAgentId != null
                    && !cachedAgentId.equals(session.getAgentId())) {
                chatRedisRepository.setRemove(
                        RedisConstants.agentSessionsKey(cachedAgentId),
                        session.getId()
                );
            }
            routingSupport.cacheActiveSessionState(session);
            routingSupport.recordVipWaitTime(session);
            activeCountByAgent.merge(session.getAgentId(), 1L, Long::sum);
        }

        Set<String> onlineAgentIds = chatRedisRepository.sortedSetRange(
                RedisConstants.AGENT_LOAD,
                0,
                -1
        );
        if (onlineAgentIds == null) {
            return;
        }
        for (String agentId : onlineAgentIds) {
            chatRedisRepository.sortedSetAdd(
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
        chatRedisRepository.sortedSetAdd(
                RedisConstants.AGENT_LOAD,
                agentId,
                activeSessionCount == null ? 0 : activeSessionCount
        );
    }

    void reconcilePendingSessionFinalizations() {
        Set<String> sessionIds = chatRedisRepository.sortedSetRangeByScore(
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
                Object rawPayload = chatRedisRepository.hashGet(
                        RedisConstants.SESSION_FINALIZE_PENDING_PAYLOAD,
                        sessionId
                );
                if (rawPayload == null) {
                    routingSupport.clearSessionFinalizePending(sessionId);
                    continue;
                }
                String[] payloadParts =
                        String.valueOf(rawPayload).split("\\|", 3);
                if (payloadParts.length != 3) {
                    routingSupport.clearSessionFinalizePending(sessionId);
                    continue;
                }
                LocalDateTime requestedEndTime =
                        LocalDateTime.parse(payloadParts[2]);
                ChatSession session =
                        chatSessionMapper.selectById(sessionId);
                if (session == null) {
                    routingSupport.clearSessionFinalizePending(sessionId);
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
                routingSupport.applySessionFinalizationRedis(
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
}
