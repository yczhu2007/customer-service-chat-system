package com.example.customerservice.config;

import com.example.customerservice.scheduler.AgentReconnectGraceScheduler;
import com.example.customerservice.scheduler.HeartbeatTimeoutScheduler;
import com.example.customerservice.scheduler.MessageReconciliationScheduler;
import com.example.customerservice.scheduler.SessionInactivityScheduler;
import com.example.customerservice.scheduler.SessionStateReconciliationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSchedulerConfigurationTest {

    @Test
    void schedulersUseConfiguredDelaysAndDoNotInjectRedisTemplate() throws Exception {
        assertConfiguredDelay(AgentReconnectGraceScheduler.class, "handleExpiredGracePeriods");
        assertConfiguredDelay(HeartbeatTimeoutScheduler.class, "scanHeartbeatTimeout");
        assertConfiguredDelay(MessageReconciliationScheduler.class, "retryFailedMessages");
        assertConfiguredDelay(SessionStateReconciliationScheduler.class, "reconcileSessionState");

        boolean directlyInjectsRedis = Arrays.stream(SessionInactivityScheduler.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .anyMatch(StringRedisTemplate.class::equals);
        assertFalse(directlyInjectsRedis);
    }

    private static void assertConfiguredDelay(Class<?> type, String methodName) throws Exception {
        Method method = type.getDeclaredMethod(methodName);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertTrue(scheduled.fixedDelayString().startsWith("${app.chat."));
    }
}
