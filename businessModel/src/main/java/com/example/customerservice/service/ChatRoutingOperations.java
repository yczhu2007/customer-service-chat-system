package com.example.customerservice.service;

import com.example.customerservice.dto.AssignResult;

/** 用户接入、客服选择和等待队列路由操作。 */
public interface ChatRoutingOperations {

    AssignResult onUserConnected(String userId);

    String findIdleAgent(String userId);

    void enqueueWaitingUser(String userId);

    boolean cancelWaitingUser(String userId);

    void backfillWaitingUsers(Iterable<String> userIds);

    void refreshWaitingPositions();

    void removeTimedOutWaitingUsers();

    void processNextWaitingUser(String agentId);
}
