package com.example.customerservice.storage;

import com.example.customerservice.config.MinioAttachmentProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioAttachmentObjectStorageTest {
    @Test
    void convertsSdkWriteFailureToStorageException() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new RuntimeException("network")).when(client).putObject(any());
        MinioAttachmentProperties properties = new MinioAttachmentProperties();
        properties.getMinio().setBucket("chat-attachments");

        MinioAttachmentObjectStorage storage = new MinioAttachmentObjectStorage(client, properties);

        assertThrows(AttachmentStorageException.class,
                () -> storage.put("a.txt", new ByteArrayInputStream(new byte[]{1}), 1, "text/plain"));
    }
}
