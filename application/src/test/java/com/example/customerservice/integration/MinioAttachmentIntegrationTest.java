package com.example.customerservice.integration;

import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.storage.AttachmentObjectStorage;
import com.example.customerservice.storage.MinioAttachmentObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@EnabledIfEnvironmentVariable(named = "RUN_REAL_INTEGRATION_TESTS", matches = "true")
class MinioAttachmentIntegrationTest {
    @Test
    void uploadsReadsStatsAndDeletesARealObject() throws Exception {
        String endpoint = env("MINIO_ENDPOINT", "http://127.0.0.1:9000");
        String accessKey = env("MINIO_ACCESS_KEY", "minioadmin");
        String secretKey = env("MINIO_SECRET_KEY", "minioadmin");
        String bucket = env("MINIO_BUCKET", "chat-attachments");
        MinioClient client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        MinioAttachmentProperties properties = new MinioAttachmentProperties();
        properties.getMinio().setBucket(bucket);
        AttachmentObjectStorage storage = new MinioAttachmentObjectStorage(client, properties);
        String objectName = "integration/" + UUID.randomUUID() + ".txt";
        byte[] content = "minio-integration".getBytes(StandardCharsets.UTF_8);
        try {
            storage.put(objectName, new ByteArrayInputStream(content), content.length, "text/plain");
            assertEquals(content.length, storage.stat(objectName).size());
            try (var input = storage.open(objectName)) {
                assertArrayEquals(content, input.readAllBytes());
            }
        } finally {
            storage.delete(objectName);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
