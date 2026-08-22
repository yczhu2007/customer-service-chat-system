package com.example.customerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionCredentialValidatorTest {

    @Test
    void productionConfigurationFailsWithoutTrustedWebSocketOrigin() {
        MockEnvironment environment = requiredEnvironment()
                .withProperty("WEBSOCKET_ALLOWED_ORIGIN_PATTERNS", "https://console.example.com,*" );

        assertThrows(IllegalStateException.class,
                () -> new ProductionCredentialValidator(environment).run(null));
    }

    @Test
    void productionConfigurationRequiresBootstrapPasswordOnlyWhenEnabled() {
        MockEnvironment environment = requiredEnvironment()
                .withProperty("WEBSOCKET_ALLOWED_ORIGIN_PATTERNS", "https://console.example.com")
                .withProperty("ADMIN_BOOTSTRAP_ENABLED", "true");

        assertThrows(IllegalStateException.class,
                () -> new ProductionCredentialValidator(environment).run(null));
    }

    @Test
    void productionConfigurationAcceptsDisabledBootstrapWithExactHttpsOrigin() {
        MockEnvironment environment = requiredEnvironment()
                .withProperty("WEBSOCKET_ALLOWED_ORIGIN_PATTERNS", "https://console.example.com")
                .withProperty("ADMIN_BOOTSTRAP_ENABLED", "false");

        assertDoesNotThrow(() -> new ProductionCredentialValidator(environment).run(null));
    }

    private MockEnvironment requiredEnvironment() {
        return new MockEnvironment()
                .withProperty("DB_URL", "jdbc:mysql://db.example.com:3306/chat")
                .withProperty("DB_USERNAME", "chat")
                .withProperty("DB_PASSWORD", "safe-password")
                .withProperty("REDIS_HOST", "redis.example.com")
                .withProperty("REDIS_PASSWORD", "safe-redis-password");
    }
}
