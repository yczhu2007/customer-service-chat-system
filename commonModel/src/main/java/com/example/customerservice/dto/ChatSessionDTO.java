package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatSession;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * WebSocket会话信息传输对象。
 */
public class ChatSessionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String event;
    private String sessionId;
    private String userId;
    private String agentId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private String endedBy;
    private String reason;

    public static ChatSessionDTO fromEntity(
            ChatSession session,
            String event
    ) {
        ChatSessionDTO dto = new ChatSessionDTO();
        dto.setEvent(event);
        dto.setSessionId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setAgentId(session.getAgentId());
        dto.setStatus(session.getStatus());
        dto.setCreateTime(session.getCreateTime());
        dto.setEndTime(session.getEndTime());
        return dto;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getEndedBy() {
        return endedBy;
    }

    public void setEndedBy(String endedBy) {
        this.endedBy = endedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
