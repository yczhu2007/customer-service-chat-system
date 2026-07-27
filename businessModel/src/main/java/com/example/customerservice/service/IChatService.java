package com.example.customerservice.service;


import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;

import java.util.List;


public interface IChatService {

    AssignResult onUserConnected(String userId);
    ChatSession createSession(String userId, String agentId);


    /**
     * 客服上线
     */
    void agentOnline(String agentId);

    /**
     * 客服下线
     */
    void agentOffline(String agentId);

    String findIdleAgent();



    void enqueueWaitingUser(String userId);
    // 通知双方
    void notifyBothParties(ChatSession session);

    /**
     * 处理客户端发送的聊天消息
     */
    int handleMessage(ChatMessage message);


    /**
     * 把消息写入Redis热缓存
     */
    void cacheMessage(ChatMessage message);


    /**
     * 找到接收方并进行WebSocket推送
     */
    void routeAndPush(ChatMessage message);

    /**
     * 由当前会话分配的客服主动结束会话
     */
    void endSessionByAgent(
            String sessionId,
            String agentId
    );


    /**
     * 通知双方会话已经结束
     */
    void notifySessionEnded(
            ChatSession session,
            String operatorId
    );

    /**
     * 为客服分配等待队列中的下一位用户
     */
    void processNextWaitingUser(String agentId);
    /**
     * 查询指定会话的聊天历史
     */
    List<ChatMessage> getHistory(
            String sessionId,
            String operatorId
    );

    /**
     * 拉取当前用户的离线消息
     */
    void pullOfflineMessages(
            String userId
    );

    /**
     * 处理客户端消息ACK
     */
    void handleAck(
            String messageId,
            String receiverId
    );
    /**
     * 处理用户或客服的WebSocket断开
     */
    void handleDisconnect(
            String userId
    );
    /**
     * 处理指定WebSocket连接发送的心跳。
     */
    void handleHeartbeat(
            String userId,
            String wsSessionId
    );


    /**
     * 处理心跳超时
     */
    void handleHeartbeatTimeout(
            String userId
    );
    void registerOnline(
            String userId,
            String wsSessionId
    );
}
