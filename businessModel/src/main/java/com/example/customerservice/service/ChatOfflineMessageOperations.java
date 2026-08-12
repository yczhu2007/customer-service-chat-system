package com.example.customerservice.service;

import com.example.customerservice.domain.ChatMessage;

/** 待确认消息缓存、离线重推和业务 ACK。 */
public interface ChatOfflineMessageOperations {

    String cacheForReceiver(String receiverId, ChatMessage message);

    void pullOfflineMessages(String userId);

    void handleAck(String messageId, String receiverId);
}
