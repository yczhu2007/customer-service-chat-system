package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class RecallMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "messageId不能为空")
    @Size(max = 64, message = "messageId长度不能超过64个字符")
    private String messageId;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
