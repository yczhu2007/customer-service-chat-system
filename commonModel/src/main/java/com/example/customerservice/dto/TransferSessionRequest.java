package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** 客服转接会话请求。 */
public class TransferSessionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 64, message = "会话ID长度不能超过64个字符")
    private String sessionId;

    @NotBlank(message = "目标客服ID不能为空")
    @Size(max = 64, message = "目标客服ID长度不能超过64个字符")
    private String targetAgentId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTargetAgentId() {
        return targetAgentId;
    }

    public void setTargetAgentId(String targetAgentId) {
        this.targetAgentId = targetAgentId;
    }
}
