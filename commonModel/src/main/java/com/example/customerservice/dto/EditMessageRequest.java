package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

public class EditMessageRequest {

    @NotBlank(message = "messageId不能为空")
    private String messageId;

    @NotBlank(message = "新消息内容不能为空")
    private String content;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
