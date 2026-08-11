package com.example.customerservice.service;

import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;

/** 会话创建、结束、关闭和排队状态通知。 */
public interface ChatSessionNotificationOperations {

    void notifyBothParties(ChatSession session, int vipLevel);

    void notifySessionEnded(ChatSession session, String operatorId);

    void notifySessionClosed(String userId, String sessionId, String reason);

    void notifyUserSession(ChatSession session, int vipLevel);

    void notifyWaitingUser(String userId, AssignResult notice);
}
