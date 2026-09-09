package com.example.customerservice.service;

import com.example.customerservice.dto.SessionSummaryVO;

/** 管理员会话和消息管理操作。 */
public interface ChatAdministrationOperations {

    SessionSummaryVO createSession(String userLoginNumber, String agentLoginNumber);

    void deleteMessage(String messageId);

    void deleteSession(String sessionId);
}
