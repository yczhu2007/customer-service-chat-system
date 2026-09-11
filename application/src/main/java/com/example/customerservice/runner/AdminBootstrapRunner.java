package com.example.customerservice.runner;

import com.example.customerservice.service.AdminBootstrapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 在显式启用配置时初始化默认管理员。
 */
@Component
@Slf4j
public class AdminBootstrapRunner
        implements ApplicationRunner {

    private final AdminBootstrapService bootstrapService;

    @Value(
            "${app.bootstrap.admin.enabled:false}"
    )
    private boolean enabled;

    @Value(
            "${app.bootstrap.admin.id:admin}"
    )
    private String adminId;

    @Value(
            "${app.bootstrap.admin.username:admin}"
    )
    private String adminUsername;

    @Value(
            "${app.bootstrap.admin.password:}"
    )
    private String adminPassword;

    public AdminBootstrapRunner(
            AdminBootstrapService bootstrapService
    ) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(
            ApplicationArguments arguments
    ) {
        if (!enabled) {
            log.info(
                    "默认管理员初始化功能已关闭"
            );

            return;
        }

        bootstrapService.initialize(adminId, adminUsername, adminPassword);
    }
}
