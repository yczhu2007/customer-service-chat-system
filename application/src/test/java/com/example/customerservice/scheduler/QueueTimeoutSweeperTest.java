package com.example.customerservice.scheduler;

import com.example.customerservice.service.impl.QueueTimeoutService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueueTimeoutSweeperTest {
    @Test
    void delegatesSweepThroughDistributedLock() {
        QueueTimeoutService service = mock(QueueTimeoutService.class);
        DistributedSchedulerLock lock = mock(DistributedSchedulerLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(eq("queue-timeout"), any(Runnable.class));

        new QueueTimeoutSweeper(service, lock).removeTimedOutUsers();

        verify(service).removeTimedOutUsers();
    }
}
