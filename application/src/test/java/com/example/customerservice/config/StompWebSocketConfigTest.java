package com.example.customerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

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

    private StompWebSocketConfig config(MockEnvironment environment, String origins) {
        return new StompWebSocketConfig(
                mock(StompAuthChannelInterceptor.class),
                mock(WebSocketHandshakeInterceptor.class),
                mock(WebSocketHandshakeHandler.class),
                new ConcurrentTaskScheduler(),
                environment,
                origins);
    }
}
