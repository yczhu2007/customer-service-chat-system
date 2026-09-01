package com.example.customerservice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Prevents deployments from silently using development defaults. */
@Component
public class RuntimeProfileValidator implements ApplicationRunner {

    private final Environment environment;

    public RuntimeProfileValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean supported = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equals(profile) || "prod".equals(profile));
        if (!supported) {
            throw new IllegalStateException("必须显式激活 dev 或 prod Profile");
        }
    }
}
