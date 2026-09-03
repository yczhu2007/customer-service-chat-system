package com.example.customerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    @Test
    void providesABoundedExecutorForClientOutboundMessages() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AsyncConfig.class)) {
            assertTrue(context.containsBean("stompOutboundExecutor"));

            ThreadPoolTaskExecutor executor = context.getBean(
                    "stompOutboundExecutor", ThreadPoolTaskExecutor.class
            );
            Future<String> threadName = executor.submit(() -> Thread.currentThread().getName());

            assertEquals(8, executor.getMaxPoolSize());
            assertEquals("stomp-outbound-", executor.getThreadNamePrefix());
            assertTrue(threadName.get().startsWith("stomp-outbound-"));
        }
    }
}
