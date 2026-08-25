package com.example.customerservice.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(MinioAttachmentProperties.class)
public class MinioAttachmentConfiguration {

    @Bean
    public MinioClient minioClient(MinioAttachmentProperties properties) {
        MinioAttachmentProperties.Minio minio = properties.getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())
                || !StringUtils.hasText(minio.getAccessKey())
                || !StringUtils.hasText(minio.getSecretKey())
                || !StringUtils.hasText(minio.getBucket())) {
            throw new IllegalStateException("MinIO endpoint、账号、密钥和 Bucket 必须配置");
        }
        return MinioClient.builder()
                .endpoint(minio.getEndpoint().trim())
                .credentials(minio.getAccessKey().trim(), minio.getSecretKey().trim())
                .build();
    }
}
