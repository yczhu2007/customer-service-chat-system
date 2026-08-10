package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

/** 将指定消息及其之前的对方消息批量标记为已读。 */
public class ReadMessagesRequest {

    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    @NotBlank(message = "lastReadMessageId不能为空")
    private String lastReadMessageId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void setLastReadMessageId(String lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }
}
