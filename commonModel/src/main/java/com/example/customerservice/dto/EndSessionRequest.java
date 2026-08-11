package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 客户端结束会话时提交的数据
 */
public class EndSessionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "sessionId不能为空")
    @Size(max = 64, message = "sessionId长度不能超过64个字符")
    private String sessionId;


    public String getSessionId() {
        return sessionId;
    }


    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
