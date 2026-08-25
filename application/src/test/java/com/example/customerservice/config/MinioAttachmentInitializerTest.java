package com.example.customerservice.config;

import com.example.customerservice.storage.MinioAttachmentInitializer;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioAttachmentInitializerTest {
    @Test
    void createsMissingBucketAndAcceptsPrivateBucket() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(false);
        when(client.getBucketPolicy(any())).thenReturn("");
        MinioAttachmentProperties properties = new MinioAttachmentProperties();

        new MinioAttachmentInitializer(client, properties).run(new DefaultApplicationArguments());

        verify(client).makeBucket(any());
    }
}
