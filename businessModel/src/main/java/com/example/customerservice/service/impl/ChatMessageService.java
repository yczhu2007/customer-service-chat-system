package com.example.customerservice.service.impl;

import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.service.ChatMessageDeliveryOperations;
import com.example.customerservice.service.ChatMessageManagementOperations;
import com.example.customerservice.service.ChatMessageOperations;

/** 聊天消息模块的统一入口，负责转交投递与管理两类操作。 */
public class ChatMessageService implements ChatMessageOperations {

    private final ChatMessageDeliveryOperations deliveryOperations;
    private final ChatMessageManagementOperations managementOperations;

    public ChatMessageService(
            ChatMessageDeliveryOperations deliveryOperations,
            ChatMessageManagementOperations managementOperations
    ) {
        this.deliveryOperations = deliveryOperations;
        this.managementOperations = managementOperations;
    }

    @Override
    public int handleMessage(ChatMessage message) {
        return deliveryOperations.handleMessage(message);
    }

    @Override
    public void cacheMessage(ChatMessage message) {
        deliveryOperations.cacheMessage(message);
    }

    @Override
    public void routeAndPush(ChatMessage message) {
        deliveryOperations.routeAndPush(message);
    }

    @Override
    public void pullOfflineMessages(String userId) {
        deliveryOperations.pullOfflineMessages(userId);
    }

    @Override
    public void handleAck(String messageId, String receiverId) {
        deliveryOperations.handleAck(messageId, receiverId);
    }

    @Override
    public MessageReadResult markMessagesRead(
            String sessionId,
            String lastReadMessageId,
            String readerId
    ) {
        return managementOperations.markMessagesRead(sessionId, lastReadMessageId, readerId);
    }

    @Override
    public long countUnreadMessages(String sessionId, String userId) {
        return managementOperations.countUnreadMessages(sessionId, userId);
    }

    @Override
    public MessageMutationResult editMessage(
            String messageId,
            String newContent,
            String operatorId
    ) {
        return managementOperations.editMessage(messageId, newContent, operatorId);
    }

    @Override
    public MessageMutationResult recallMessage(String messageId, String operatorId) {
        return managementOperations.recallMessage(messageId, operatorId);
    }

    @Override
    public ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            String beforeMessageId,
            int pageSize
    ) {
        return managementOperations.getHistory(sessionId, operatorId, beforeMessageId, pageSize);
    }
}
