package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

public class RecallMessageRequest {

    @NotBlank(message = "messageId不能为空")
    private String messageId;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
