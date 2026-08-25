package com.example.customerservice.migration;

import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.service.impl.ChatAttachmentMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ChatAttachmentMigrationRunner implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentMigrationRunner.class);
    private final ChatAttachmentMigrationService migrationService;
    private final MinioAttachmentProperties properties;
    private final ConfigurableApplicationContext context;

    public ChatAttachmentMigrationRunner(ChatAttachmentMigrationService migrationService,
                                         MinioAttachmentProperties properties,
                                         ConfigurableApplicationContext context) {
        this.migrationService = migrationService;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.getMigration().isEnabled()) return;
        int migrated = migrationService.migrate();
        log.info("历史附件迁移完成，迁移 {} 个文件；本地附件目录已停用", migrated);
        int exitCode = SpringApplication.exit(context, () -> 0);
        if (exitCode != 0) {
            throw new IllegalStateException("历史附件迁移完成，但应用退出失败，退出码=" + exitCode);
        }
    }
}
