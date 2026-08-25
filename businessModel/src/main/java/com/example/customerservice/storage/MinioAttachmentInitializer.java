package com.example.customerservice.storage;

import com.example.customerservice.config.MinioAttachmentProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Order(10)
public class MinioAttachmentInitializer implements ApplicationRunner {
    private final MinioClient client;
    private final MinioAttachmentProperties properties;

    public MinioAttachmentInitializer(MinioClient client, MinioAttachmentProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.getMinio().getBucket();
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            ensurePrivate(bucket);
            ensureLegacyStorageState();
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO 不可用或附件 Bucket 初始化失败", exception);
        }
    }

    private void ensureLegacyStorageState() throws IOException {
        Path legacyRoot = properties.getMigration().getLegacyStoragePath().toAbsolutePath().normalize();
        Path pending = legacyRoot.resolveSibling(legacyRoot.getFileName() + ".pending-delete");
        if (properties.getMigration().isEnabled()) return;
        boolean hasLegacyFiles = false;
        if (Files.isDirectory(legacyRoot)) {
            try (var entries = Files.walk(legacyRoot)) {
                hasLegacyFiles = entries.anyMatch(Files::isRegularFile);
            }
        }
        if (Files.exists(pending) || hasLegacyFiles) {
            throw new IllegalStateException("发现未完成迁移的本地附件，请先使用 ATTACHMENT_MIGRATION_ENABLED=true 重试迁移");
        }
    }

    private void ensurePrivate(String bucket) throws Exception {
        String policy;
        try {
            policy = client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException exception) {
            if ("NoSuchBucketPolicy".equals(exception.errorResponse().code())) return;
            throw exception;
        }
        if (StringUtils.hasText(policy) && isPublicPolicy(policy)) {
            throw new IllegalStateException("附件 Bucket 必须保持私有，禁止公开读取策略");
        }
    }

    private boolean isPublicPolicy(String policy) {
        String normalized = policy.replaceAll("\\s+", "").toLowerCase();
        return normalized.contains("\"principal\":\"*\"")
                || normalized.contains("\"principal\":{\"aws\":\"*\"");
    }
}
