package com.example.customerservice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/** Prevents a production instance from starting with missing credentials. */
@Component
@Profile("prod")
public class ProductionCredentialValidator implements ApplicationRunner {

    private static final List<String> REQUIRED_ENVIRONMENT_VARIABLES = List.of(
            "DB_URL",
            "DB_USERNAME",
            "DB_PASSWORD",
            "REDIS_HOST",
            "REDIS_PASSWORD",
            "WEBSOCKET_ALLOWED_ORIGIN_PATTERNS"
    );

    private final Environment environment;

    public ProductionCredentialValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String variableName : REQUIRED_ENVIRONMENT_VARIABLES) {
            String value = environment.getProperty(variableName);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "生产环境缺少必需环境变量：" + variableName
                );
            }
        }
        String origins = environment.getProperty("WEBSOCKET_ALLOWED_ORIGIN_PATTERNS", "");
        if (origins.contains("*") || origins.toLowerCase().contains("localhost")
                || origins.contains("127.0.0.1")) {
            throw new IllegalStateException("生产环境WEBSOCKET_ALLOWED_ORIGIN_PATTERNS必须为精确的非本地来源");
        }
        if (Boolean.parseBoolean(environment.getProperty("ADMIN_BOOTSTRAP_ENABLED", "false"))) {
            String password = environment.getProperty("ADMIN_BOOTSTRAP_PASSWORD");
            if (password == null || password.isBlank() || password.length() < 12) {
                throw new IllegalStateException("启用管理员初始化时必须设置至少12位ADMIN_BOOTSTRAP_PASSWORD");
            }
        }
    }
}
