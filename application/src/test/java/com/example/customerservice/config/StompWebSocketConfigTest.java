package com.example.customerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompWebSocketConfigTest {

    @Test
    void rejectsDefaultLocalOriginsInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        StompWebSocketConfig config = config(environment,
                "http://localhost:*,http://127.0.0.1:*");

        assertThrows(IllegalStateException.class, config::validateProductionOriginConfiguration);
    }

    @Test
    void acceptsExplicitTrustedHttpsOriginInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        StompWebSocketConfig config = config(environment,
                "https://chat.example.com");

        assertDoesNotThrow(config::validateProductionOriginConfiguration);
    }

    @Test
    void configuresASeparateExecutorForClientOutboundMessages() {
        ChannelRegistration registration = mock(ChannelRegistration.class);

        config(new MockEnvironment(), "http://localhost:*")
                .configureClientOutboundChannel(registration);

        verify(registration).taskExecutor(any(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class));
    }

    @Test
    void preservesMessageOrderForEachWebSocketSession() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration broker = mock(SimpleBrokerRegistration.class);
        org.mockito.Mockito.when(registry.enableSimpleBroker("/queue", "/topic"))
                .thenReturn(broker);
        org.mockito.Mockito.when(broker.setHeartbeatValue(any(long[].class))).thenReturn(broker);
        org.mockito.Mockito.when(broker.setTaskScheduler(any())).thenReturn(broker);

        config(new MockEnvironment(), "http://localhost:*")
                .configureMessageBroker(registry);

        verify(registry).setPreservePublishOrder(true);
    }

    private StompWebSocketConfig config(MockEnvironment environment, String origins) {
        return new StompWebSocketConfig(
                mock(StompAuthChannelInterceptor.class),
                mock(WebSocketHandshakeInterceptor.class),
                mock(WebSocketHandshakeHandler.class),
                new ConcurrentTaskScheduler(),
                outboundExecutor(),
                environment,
                origins);
    }

    private ThreadPoolTaskExecutor outboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        return executor;
    }
}
