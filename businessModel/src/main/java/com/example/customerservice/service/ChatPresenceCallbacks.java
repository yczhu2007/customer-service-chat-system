package com.example.customerservice.service;

import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;

/** 在线状态服务回调核心分配和会话流程所需的最小边界。 */
public interface ChatPresenceCallbacks {

    AssignResult reconnectUser(String userId);

    void restoreAgentOnline(String agentId);

    boolean finalizePresenceSession(ChatSession session, String expectedAgentId);

    int resolveVipLevel(String userId);

    void publishSessionClosed(String userId, String sessionId, String reason);

    void reassignDisconnectedUser(String userId);
}
