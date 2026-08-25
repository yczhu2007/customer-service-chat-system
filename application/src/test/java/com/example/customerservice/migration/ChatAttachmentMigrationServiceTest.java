package com.example.customerservice.migration;

import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.service.impl.ChatAttachmentMigrationService;
import com.example.customerservice.storage.AttachmentObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatAttachmentMigrationServiceTest {
    @TempDir
    Path legacyRoot;

    @Test
    void uploadsAndVerifiesEveryLegacyFileBeforeDeletingIt() throws Exception {
        ChatAttachmentMapper mapper = mock(ChatAttachmentMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        MinioAttachmentProperties properties = new MinioAttachmentProperties();
        properties.getMigration().setLegacyStoragePath(legacyRoot);

        ChatAttachment attachment = new ChatAttachment();
        attachment.setId("A1");
        attachment.setStoredName("A1.txt");
        attachment.setFileSize(5L);
        attachment.setContentType("text/plain");
        Files.writeString(legacyRoot.resolve("A1.txt"), "hello");
        when(mapper.selectList(any())).thenReturn(List.of(attachment));
        when(storage.stat("A1.txt")).thenReturn(new AttachmentObjectStorage.StoredObject("A1.txt", 5L, Instant.now()));
        when(storage.open("A1.txt")).thenReturn(new ByteArrayInputStream("hello".getBytes()));

        int migrated = new ChatAttachmentMigrationService(mapper, storage, properties).migrate();

        assertEquals(1, migrated);
        assertFalse(Files.exists(legacyRoot.resolve("A1.txt")));
        verify(storage).put(eq("A1.txt"), any(), eq(5L), eq("text/plain"));
        verify(storage).stat("A1.txt");
    }
}
