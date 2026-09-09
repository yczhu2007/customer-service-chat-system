package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatMaintenanceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/** 将长时间没有新消息的活动会话转分配给其他可用客服。 */
@Component
@Slf4j
public class SessionInactivityScheduler {

    private final ChatMaintenanceOperations chatMaintenanceOperations;
    private final long inactivityTimeoutMillis;
    private final DistributedSchedulerLock schedulerLock;

    @Autowired
    public SessionInactivityScheduler(
            ChatMaintenanceOperations chatMaintenanceOperations,
            DistributedSchedulerLock schedulerLock,
            @Value("${app.chat.session.inactivity-timeout-seconds:1800}")
            long inactivityTimeoutSeconds
    ) {
        this.chatMaintenanceOperations = chatMaintenanceOperations;
        this.schedulerLock = schedulerLock;
        this.inactivityTimeoutMillis = TimeUnit.SECONDS.toMillis(
                Math.max(60L, inactivityTimeoutSeconds)
        );
    }

    /** 兼容旧测试/调用方，Redis 访问已由业务服务负责。 */
    public SessionInactivityScheduler(StringRedisTemplate ignoredRedisTemplate,
                                      ChatMaintenanceOperations operations,
                                      DistributedSchedulerLock lock,
                                      long inactivityTimeoutSeconds) {
        this(operations, lock, inactivityTimeoutSeconds);
    }

    @Scheduled(
            fixedDelayString = "${app.chat.session.inactivity-sweep-delay-ms:30000}"
    )
    public void reassignInactiveSessions() {
        schedulerLock.execute(
                "session-inactivity",
                this::reassignInactiveSessionsLocked
        );
    }

    private void reassignInactiveSessionsLocked() {
        long cutoffMillis = System.currentTimeMillis() - inactivityTimeoutMillis;
        chatMaintenanceOperations.handleInactiveSessions(cutoffMillis, 100);
    }
}
