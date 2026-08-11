package com.example.customerservice.service.impl;

import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.service.IChatService;

import java.util.Set;

/**
 * 聊天模块统一入口。
 *
 * <p>本类只负责对外提供稳定的 {@link IChatService} 接口；消息处理、在线状态以及
 * 分配和会话生命周期分别由对应的领域服务完成。</p>
 */
public class ChatServiceImpl implements IChatService {

    private final IChatService routingSessionService;

    public ChatServiceImpl(IChatService routingSessionService) {
        this.routingSessionService = routingSessionService;
    }

    @Override
    public AssignResult onUserConnected(String userId) {
        return routingSessionService.onUserConnected(userId);
    }

    @Override
    public ChatSession createSession(String userId, String agentId) {
        return routingSessionService.createSession(userId, agentId);
    }

    @Override
    public void agentOnline(String agentId) {
        routingSessionService.agentOnline(agentId);
    }

    @Override
    public void agentOffline(String agentId) {
        routingSessionService.agentOffline(agentId);
    }

    @Override
    public void setAgentVipSkill(String agentId, boolean enabled) {
        routingSessionService.setAgentVipSkill(agentId, enabled);
    }

    @Override
    public Set<String> findVipSkillAgentIds() {
        return routingSessionService.findVipSkillAgentIds();
    }

    @Override
    public String findIdleAgent(String userId) {
        return routingSessionService.findIdleAgent(userId);
    }

    @Override
    public void enqueueWaitingUser(String userId) {
        routingSessionService.enqueueWaitingUser(userId);
    }

    @Override
    public void refreshWaitingPositions() {
        routingSessionService.refreshWaitingPositions();
    }

    @Override
    public void notifyBothParties(ChatSession session) {
        routingSessionService.notifyBothParties(session);
    }

    @Override
    public int handleMessage(ChatMessage message) {
        return routingSessionService.handleMessage(message);
    }

    @Override
    public void cacheMessage(ChatMessage message) {
        routingSessionService.cacheMessage(message);
    }

    @Override
    public void routeAndPush(ChatMessage message) {
        routingSessionService.routeAndPush(message);
    }

    @Override
    public void endSessionByAgent(String sessionId, String agentId) {
        routingSessionService.endSessionByAgent(sessionId, agentId);
    }

    @Override
    public void transferSession(
            String sessionId,
            String sourceAgentId,
            String targetAgentId
    ) {
        routingSessionService.transferSession(sessionId, sourceAgentId, targetAgentId);
    }

    @Override
    public void notifySessionEnded(ChatSession session, String operatorId) {
        routingSessionService.notifySessionEnded(session, operatorId);
    }

    @Override
    public void processNextWaitingUser(String agentId) {
        routingSessionService.processNextWaitingUser(agentId);
    }

    @Override
    public ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            int pageNo,
            int pageSize
    ) {
        return routingSessionService.getHistory(sessionId, operatorId, pageNo, pageSize);
    }

    @Override
    public void pullOfflineMessages(String userId) {
        routingSessionService.pullOfflineMessages(userId);
    }

    @Override
    public void handleAck(String messageId, String receiverId) {
        routingSessionService.handleAck(messageId, receiverId);
    }

    @Override
    public MessageReadResult markMessagesRead(
            String sessionId,
            String lastReadMessageId,
            String readerId
    ) {
        return routingSessionService.markMessagesRead(
                sessionId, lastReadMessageId, readerId
        );
    }

    @Override
    public long countUnreadMessages(String sessionId, String userId) {
        return routingSessionService.countUnreadMessages(sessionId, userId);
    }

    @Override
    public MessageMutationResult editMessage(
            String messageId,
            String newContent,
            String operatorId
    ) {
        return routingSessionService.editMessage(messageId, newContent, operatorId);
    }

    @Override
    public MessageMutationResult recallMessage(String messageId, String operatorId) {
        return routingSessionService.recallMessage(messageId, operatorId);
    }

    @Override
    public void handleDisconnect(String userId) {
        routingSessionService.handleDisconnect(userId);
    }

    @Override
    public void handleHeartbeat(String userId, String wsSessionId) {
        routingSessionService.handleHeartbeat(userId, wsSessionId);
    }

    @Override
    public void handleHeartbeatTimeout(String userId) {
        routingSessionService.handleHeartbeatTimeout(userId);
    }

    @Override
    public void handleAgentReconnectGraceTimeout(String agentId) {
        routingSessionService.handleAgentReconnectGraceTimeout(agentId);
    }

    @Override
    public void reconcilePendingAssignments() {
        routingSessionService.reconcilePendingAssignments();
    }

    @Override
    public void reconcilePendingSessionFinalizations() {
        routingSessionService.reconcilePendingSessionFinalizations();
    }

    @Override
    public void reconcileActiveSessionState() {
        routingSessionService.reconcileActiveSessionState();
    }

    @Override
    public void handleSessionInactivityTimeout(String sessionId, long cutoffMillis) {
        routingSessionService.handleSessionInactivityTimeout(sessionId, cutoffMillis);
    }

    @Override
    public void registerOnline(String userId, String wsSessionId) {
        routingSessionService.registerOnline(userId, wsSessionId);
    }
}
