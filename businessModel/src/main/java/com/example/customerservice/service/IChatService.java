package com.example.customerservice.service;


import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.MessageMutationResult;

import java.util.List;
import java.util.Set;


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

    /** 管理VIP坐席技能组。 */
    void setAgentVipSkill(String agentId, boolean enabled);

    /** 查询当前VIP坐席技能组。 */
    Set<String> findVipSkillAgentIds();

    String findIdleAgent(String userId);



    void enqueueWaitingUser(String userId);
    void refreshWaitingPositions();
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

    /** 将当前客服持有的活动会话转接给目标客服。 */
    void transferSession(
            String sessionId,
            String sourceAgentId,
            String targetAgentId
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
    ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            int pageNo,
            int pageSize
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

    /** 持久化当前会话参与者的批量已读状态并返回剩余未读数。 */
    MessageReadResult markMessagesRead(
            String sessionId,
            String lastReadMessageId,
            String readerId
    );

    /** 查询当前会话参与者的未读消息数量。 */
    long countUnreadMessages(
            String sessionId,
            String userId
    );

    /** 编辑当前用户在允许时间内发送的文本消息。 */
    MessageMutationResult editMessage(
            String messageId,
            String newContent,
            String operatorId
    );

    /** 撤回当前用户在允许时间内发送的消息。 */
    MessageMutationResult recallMessage(
            String messageId,
            String operatorId
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
    void handleAgentReconnectGraceTimeout(String agentId);
    void reconcilePendingAssignments();
    void reconcilePendingSessionFinalizations();
    void reconcileActiveSessionState();
    void handleSessionInactivityTimeout(String sessionId, long cutoffMillis);
    void registerOnline(
            String userId,
            String wsSessionId
    );
}
