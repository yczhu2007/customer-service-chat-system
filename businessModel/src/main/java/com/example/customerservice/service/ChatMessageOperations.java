package com.example.customerservice.service;

import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.ChatHistoryPage;

/** 聊天消息、历史记录、已读状态和消息变更操作。 */
public interface ChatMessageOperations {

    int handleMessage(ChatMessage message);

    void cacheMessage(ChatMessage message);

    void routeAndPush(ChatMessage message);

    void pullOfflineMessages(String userId);

    void handleAck(String messageId, String receiverId);

    MessageReadResult markMessagesRead(
            String sessionId,
            String lastReadMessageId,
            String readerId
    );

    long countUnreadMessages(String sessionId, String userId);

    MessageMutationResult recallMessage(String messageId, String operatorId);

    ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            String beforeMessageId,
            int pageSize
    );

}
