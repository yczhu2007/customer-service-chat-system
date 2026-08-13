package com.example.customerservice.service;

import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;

/** 消息已读、编辑、撤回及历史查询操作。 */
public interface ChatMessageManagementOperations {

    MessageReadResult markMessagesRead(String sessionId, String lastReadMessageId, String readerId);

    long countUnreadMessages(String sessionId, String userId);

    MessageMutationResult editMessage(String messageId, String newContent, String operatorId);

    MessageMutationResult recallMessage(String messageId, String operatorId);

    ChatHistoryPage getHistory(String sessionId, String operatorId, String beforeMessageId, int pageSize);
}
