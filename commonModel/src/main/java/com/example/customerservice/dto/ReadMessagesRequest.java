package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** 将指定消息及其之前的对方消息批量标记为已读。 */
public class ReadMessagesRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "sessionId不能为空")
    @Size(max = 64, message = "sessionId长度不能超过64个字符")
    private String sessionId;

    @NotBlank(message = "lastReadMessageId不能为空")
    @Size(max = 64, message = "lastReadMessageId长度不能超过64个字符")
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
