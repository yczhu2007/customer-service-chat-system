package com.example.customerservice.service.impl;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AssignResult;
import com.example.customerservice.dto.ChatSessionDTO;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

/** 只负责将已经生成好的会话业务结果发送给 WebSocket 客户端。 */
@Slf4j
public class ChatSessionNotificationService implements ChatSessionNotificationOperations {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatSessionNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void notifyBothParties(ChatSession session, int vipLevel) {
        AssignResult notice = AssignResult.assigned(session);
        notice.setVipLevel(vipLevel);
        messagingTemplate.convertAndSendToUser(
                session.getUserId(), "/queue/chat", notice
        );
        messagingTemplate.convertAndSendToUser(
                session.getAgentId(), "/queue/chat", notice
        );
        log.info("会话创建通知已发送，用户：{}，客服：{}", session.getUserId(), session.getAgentId());
    }

    @Override
    public void notifySessionEnded(ChatSession session, String operatorId) {
        ChatSessionDTO notice = ChatSessionDTO.fromEntity(
                session,
                ChatConstants.EVENT_SESSION_ENDED
        );
        notice.setEndedBy(operatorId);
        messagingTemplate.convertAndSendToUser(
                session.getUserId(), "/queue/chat", notice
        );
        messagingTemplate.convertAndSendToUser(
                session.getAgentId(), "/queue/chat", notice
        );
        log.info("会话结束通知已发送，用户：{}，客服：{}", session.getUserId(), session.getAgentId());
    }

    @Override
    public void notifySessionClosed(String userId, String sessionId, String reason) {
        Map<String, Object> notice = new HashMap<>();
        notice.put("event", ChatConstants.EVENT_SESSION_CLOSED);
        notice.put("sessionId", sessionId);
        notice.put("reason", reason);
        messagingTemplate.convertAndSendToUser(userId, "/queue/chat", notice);
    }

    @Override
    public void notifyUserSession(ChatSession session, int vipLevel) {
        AssignResult notice = AssignResult.reconnected(session);
        notice.setVipLevel(vipLevel);
        messagingTemplate.convertAndSendToUser(
                session.getUserId(), "/queue/chat", notice
        );
    }

    @Override
    public void notifyWaitingUser(String userId, AssignResult notice) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/chat", notice);
    }
}
