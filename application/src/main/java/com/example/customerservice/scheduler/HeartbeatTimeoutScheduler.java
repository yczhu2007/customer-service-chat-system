package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatPresenceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * 定时扫描心跳超时用户
 */
@Component
@Slf4j
public class HeartbeatTimeoutScheduler {

    private final ChatPresenceOperations chatPresenceOperations;
    private final DistributedSchedulerLock schedulerLock;


    public HeartbeatTimeoutScheduler(
            ChatPresenceOperations chatPresenceOperations,
            DistributedSchedulerLock schedulerLock
    ) {

        this.chatPresenceOperations =
                chatPresenceOperations;
        this.schedulerLock = schedulerLock;
    }


    /**
     * 每10秒执行一次。
     * fixedDelay表示：
     * 上一次任务结束10秒后，
     * 再执行下一次任务。
     */
    @Scheduled(fixedDelayString = "${app.chat.heartbeat-sweep-delay-ms:10000}")
    public void scanHeartbeatTimeout() {
        schedulerLock.execute(
                "heartbeat-timeout",
                this::scanHeartbeatTimeoutLocked
        );
    }

    private void scanHeartbeatTimeoutLocked() {

        chatPresenceOperations.handleExpiredHeartbeatUsers(System.currentTimeMillis(), 100);
    }
}
