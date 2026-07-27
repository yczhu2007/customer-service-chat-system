package com.example.customerservice.dto;


/**
 * 客户端结束会话时提交的数据
 */
public class EndSessionRequest {

    private String sessionId;


    public String getSessionId() {
        return sessionId;
    }


    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}