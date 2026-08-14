package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 消息落库死信记录。 */
public class DeadLetterMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private LocalDateTime failedAt;
    private boolean payloadAvailable;
    private String sessionId;
    private String senderId;
    private String messageType;

    public DeadLetterMessageVO() {
    }

    public DeadLetterMessageVO(
            String messageId,
            LocalDateTime failedAt,
            boolean payloadAvailable,
            String sessionId,
            String senderId,
            String messageType
    ) {
        this.messageId = messageId;
        this.failedAt = failedAt;
        this.payloadAvailable = payloadAvailable;
        this.sessionId = sessionId;
        this.senderId = senderId;
        this.messageType = messageType;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public boolean isPayloadAvailable() {
        return payloadAvailable;
    }

    public void setPayloadAvailable(boolean payloadAvailable) {
        this.payloadAvailable = payloadAvailable;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
}
