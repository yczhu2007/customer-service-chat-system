package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

/** 客服转接会话请求。 */
public class TransferSessionRequest {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "目标客服ID不能为空")
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
