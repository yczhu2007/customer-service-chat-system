package com.example.customerservice.service;

/** 会话状态补偿和超时维护操作。 */
public interface ChatMaintenanceOperations {

    void handleInactiveSessions(long cutoffMillis, int batchSize);

    void reconcilePendingAssignments();

    void reconcilePendingSessionFinalizations();

    void reconcileActiveSessionState();

    void handleSessionInactivityTimeout(String sessionId, long cutoffMillis);
}
