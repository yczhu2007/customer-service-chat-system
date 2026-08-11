package com.example.customerservice.service;

/** WebSocket在线状态、心跳、断线与客服重连宽限期操作。 */
public interface ChatPresenceOperations {

    void handleDisconnect(String userId);

    void handleHeartbeat(String userId, String wsSessionId);

    void handleHeartbeatTimeout(String userId);

    void handleAgentReconnectGraceTimeout(String agentId);

    void registerOnline(String userId, String wsSessionId);

    void disconnectAgent(String agentId, String reason);
}
