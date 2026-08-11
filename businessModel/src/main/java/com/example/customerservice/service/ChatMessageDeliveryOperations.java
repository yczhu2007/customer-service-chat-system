package com.example.customerservice.service;

import com.example.customerservice.domain.ChatMessage;

/** 消息发送、缓存、离线投递和确认操作。 */
public interface ChatMessageDeliveryOperations {

    int handleMessage(ChatMessage message);

    void cacheMessage(ChatMessage message);

    void routeAndPush(ChatMessage message);

    void pullOfflineMessages(String userId);

    void handleAck(String messageId, String receiverId);
}
