package com.example.customerservice.service;

import com.example.customerservice.domain.ChatSession;

/** 会话创建、结束、转接和通知操作。 */
public interface ChatSessionOperations {

    ChatSession createSession(String userId, String agentId);

    void endSessionByAgent(String sessionId, String agentId);

    void transferSession(String sessionId, String sourceAgentId, String targetAgentId);

    void notifyBothParties(ChatSession session);

    void notifySessionEnded(ChatSession session, String operatorId);
}
