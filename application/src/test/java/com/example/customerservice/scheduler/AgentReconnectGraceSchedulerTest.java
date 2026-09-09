package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatPresenceOperations;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentReconnectGraceSchedulerTest {

    @Test
    void delegatesBatchThroughDistributedLock() {
        ChatPresenceOperations service = mock(ChatPresenceOperations.class);
        DistributedSchedulerLock lock = mock(DistributedSchedulerLock.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(lock).execute(eq("agent-reconnect-grace"), any(Runnable.class));

        new AgentReconnectGraceScheduler(service, lock).handleExpiredGracePeriods();

        verify(service).handleExpiredReconnectGracePeriods(anyLong(), eq(100));
    }
}
