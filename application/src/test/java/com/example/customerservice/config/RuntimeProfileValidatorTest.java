package com.example.customerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeProfileValidatorTest {

    @Test
    void failsWhenNoRuntimeProfileIsActive() {
        assertThrows(IllegalStateException.class,
                () -> new RuntimeProfileValidator(new MockEnvironment()).run(null));
    }

    @Test
    void acceptsExplicitDevelopmentOrProductionProfile() {
        MockEnvironment development = new MockEnvironment();
        development.setActiveProfiles("dev");
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new RuntimeProfileValidator(development).run(null));
        assertDoesNotThrow(() -> new RuntimeProfileValidator(production).run(null));
    }
}
