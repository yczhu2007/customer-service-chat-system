package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class EditMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "messageId不能为空")
    @Size(max = 64, message = "messageId长度不能超过64个字符")
    private String messageId;

    @NotBlank(message = "新消息内容不能为空")
    @Size(max = 4000, message = "新消息内容不能超过4000个字符")
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
