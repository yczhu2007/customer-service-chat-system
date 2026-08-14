package com.example.customerservice.service;

/** 会话状态补偿和超时维护操作。 */
public interface ChatMaintenanceOperations {

    void reconcilePendingAssignments();

    void reconcilePendingSessionFinalizations();

    void reconcileActiveSessionState();

    void handleSessionInactivityTimeout(String sessionId, long cutoffMillis);
}
