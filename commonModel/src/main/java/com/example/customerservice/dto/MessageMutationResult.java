package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatMessage;

import java.time.LocalDateTime;
import java.io.Serializable;

/** 消息撤回后推送给会话双方的事件。 */
public class MessageMutationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String event;
    private String messageId;
    private String sessionId;
    private String operatorId;
    private String content;
    private boolean recalled;
    private LocalDateTime recalledAt;

    public static MessageMutationResult recalled(ChatMessage message) {
        MessageMutationResult result = from(message);
        result.setEvent("MESSAGE_RECALLED");
        result.setContent(null);
        return result;
    }

    private static MessageMutationResult from(ChatMessage message) {
        MessageMutationResult result = new MessageMutationResult();
        result.setMessageId(message.getId());
        result.setSessionId(message.getSessionId());
        result.setOperatorId(message.getSenderId());
        result.setRecalled(Boolean.TRUE.equals(message.getRecalled()));
        result.setRecalledAt(message.getRecalledAt());
        return result;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isRecalled() { return recalled; }
    public void setRecalled(boolean recalled) { this.recalled = recalled; }
    public LocalDateTime getRecalledAt() { return recalledAt; }
    public void setRecalledAt(LocalDateTime recalledAt) { this.recalledAt = recalledAt; }
}
