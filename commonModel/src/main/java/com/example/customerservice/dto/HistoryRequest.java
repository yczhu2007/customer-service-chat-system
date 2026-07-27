package com.example.customerservice.dto;


/**
 * 查询聊天历史时客户端提交的数据
 */
public class HistoryRequest {

    private String sessionId;


    public String getSessionId() {
        return sessionId;
    }


    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}