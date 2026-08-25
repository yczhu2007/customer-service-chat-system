package com.example.customerservice.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinioConfigurationTest {

    @Test
    void storesMinioConnectionAndMigrationSettings() {
        MinioAttachmentProperties properties = new MinioAttachmentProperties();
        properties.getMinio().setEndpoint("http://minio:9000");
        properties.getMinio().setAccessKey("access");
        properties.getMinio().setSecretKey("secret");
        properties.getMinio().setBucket("chat-attachments");
        properties.getMigration().setEnabled(true);

        assertEquals("http://minio:9000", properties.getMinio().getEndpoint());
        assertEquals("access", properties.getMinio().getAccessKey());
        assertEquals("secret", properties.getMinio().getSecretKey());
        assertEquals("chat-attachments", properties.getMinio().getBucket());
        assertEquals(true, properties.getMigration().isEnabled());
    }
}
