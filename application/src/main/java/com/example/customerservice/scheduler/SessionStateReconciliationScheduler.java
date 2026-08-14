package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatMaintenanceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统一补偿会话创建分配和会话结束过程中的跨存储状态。
 */
@Component
@Slf4j
public class SessionStateReconciliationScheduler {

    private final ChatMaintenanceOperations chatMaintenanceOperations;

    public SessionStateReconciliationScheduler(
            ChatMaintenanceOperations chatMaintenanceOperations
    ) {
        this.chatMaintenanceOperations = chatMaintenanceOperations;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcileSessionState() {
        try {
            chatMaintenanceOperations.reconcileActiveSessionState();
        } catch (Exception exception) {
            log.error("Active session state reconciliation failed", exception);
        }
        try {
            chatMaintenanceOperations.reconcilePendingAssignments();
        } catch (Exception exception) {
            log.error("会话创建状态对账任务执行失败", exception);
        }
        try {
            chatMaintenanceOperations.reconcilePendingSessionFinalizations();
        } catch (Exception exception) {
            log.error("会话结束状态对账任务执行失败", exception);
        }
    }
}
