package com.example.customerservice.service;

import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.service.impl.ChatAttachmentServiceImpl;
import com.example.customerservice.storage.AttachmentObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatAttachmentServiceImplTest {
    @Test
    void uploadIgnoresForgedContentTypeAndUsesServerMimeMapping() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        ChatSession session = new ChatSession(); session.setId("S1"); session.setUserId("U1"); session.setAgentId("A1");
        when(sessionMapper.selectById("S1")).thenReturn(session);
        AtomicReference<ChatAttachment> saved = new AtomicReference<>();
        when(attachmentMapper.insert(any(ChatAttachment.class))).thenAnswer(invocation -> { saved.set(invocation.getArgument(0)); return 1; });
        when(attachmentMapper.selectById(anyString())).thenAnswer(invocation -> saved.get());
        ChatAttachmentServiceImpl service = new ChatAttachmentServiceImpl(attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties());

        var result = service.upload("U1", "S1",
                new MockMultipartFile("file", "safe.jpg", "text/html", new byte[]{
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01
                }));

        assertEquals("image/jpeg", result.getContentType());
        assertEquals("IMAGE", result.getMessageType());
    }

    @Test
    void uploadRejectsContentThatDoesNotMatchItsExtension() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        ChatSession session = new ChatSession(); session.setId("S1"); session.setUserId("U1"); session.setAgentId("A1");
        when(sessionMapper.selectById("S1")).thenReturn(session);
        ChatAttachmentServiceImpl service = new ChatAttachmentServiceImpl(attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties());

        assertThrows(IllegalArgumentException.class, () -> service.upload("U1", "S1",
                new MockMultipartFile("file", "payload.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.upload("U1", "S1",
                new MockMultipartFile("file", "payload.zip", "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04})));
    }

    @Test
    void uploadRejectsUnsupportedExtensionAndNonParticipant() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        ChatSession session = new ChatSession(); session.setId("S1"); session.setUserId("U1"); session.setAgentId("A1");
        when(sessionMapper.selectById("S1")).thenReturn(session);
        ChatAttachmentServiceImpl service = new ChatAttachmentServiceImpl(attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties());
        assertThrows(IllegalArgumentException.class, () -> service.upload("X1", "S1",
                new MockMultipartFile("file", "safe.jpg", "image/jpeg", new byte[]{1})));
        assertThrows(IllegalArgumentException.class, () -> service.upload("U1", "S1",
                new MockMultipartFile("file", "attack.html", "text/html", new byte[]{1})));
    }
}
