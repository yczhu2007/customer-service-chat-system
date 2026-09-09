package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatMaintenanceOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class SessionInactivitySchedulerTest {
    @Mock private ChatMaintenanceOperations chatMaintenanceOperations;
    @Mock private DistributedSchedulerLock schedulerLock;

    private SessionInactivityScheduler scheduler;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(schedulerLock).execute(anyString(), any(Runnable.class));
        scheduler = new SessionInactivityScheduler(
                null,
                chatMaintenanceOperations,
                schedulerLock,
                60
        );
    }

    @Test
    void reassignsEveryExpiredSession() {
        scheduler.reassignInactiveSessions();
        verify(chatMaintenanceOperations).handleInactiveSessions(anyLong(), org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void doesNothingWhenNoSessionExpired() {
        scheduler.reassignInactiveSessions();
    }
}
