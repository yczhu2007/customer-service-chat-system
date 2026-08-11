package com.example.customerservice.service;

/** 客服会话转接操作。 */
public interface ChatSessionTransferOperations {

    void transferSession(String sessionId, String sourceAgentId, String targetAgentId);
}
