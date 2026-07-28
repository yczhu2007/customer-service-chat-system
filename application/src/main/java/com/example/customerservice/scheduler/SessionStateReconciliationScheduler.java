package com.example.customerservice.scheduler;

import com.example.customerservice.service.IChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统一补偿会话创建分配和会话结束过程中的跨存储状态。
 */
@Component
public class SessionStateReconciliationScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SessionStateReconciliationScheduler.class);

    private final IChatService chatService;

    public SessionStateReconciliationScheduler(IChatService chatService) {
        this.chatService = chatService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcileSessionState() {
        try {
            chatService.reconcilePendingAssignments();
        } catch (Exception exception) {
            LOGGER.error("会话创建状态对账任务执行失败", exception);
        }
        try {
            chatService.reconcilePendingSessionFinalizations();
        } catch (Exception exception) {
            LOGGER.error("会话结束状态对账任务执行失败", exception);
        }
    }
}
