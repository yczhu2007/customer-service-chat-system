package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AttachmentDownload;
import com.example.customerservice.dto.AttachmentUpload;
import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.service.impl.ChatAttachmentServiceImpl;
import com.example.customerservice.service.OfficePreviewConverter;
import com.example.customerservice.storage.AttachmentObjectStorage;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatAttachmentServiceImplTest {
    @Test
    void publicAttachmentServiceDoesNotExposePersistenceEntity() {
        boolean exposesEntity = java.util.Arrays.stream(ChatAttachmentService.class.getMethods())
                .anyMatch(method -> method.getReturnType() == ChatAttachment.class
                        || java.util.Arrays.asList(method.getParameterTypes()).contains(ChatAttachment.class));
        assertFalse(exposesEntity);
    }

    @Test
    void publicAttachmentServiceDoesNotExposeWebUploadType() {
        boolean exposesMultipartFile = java.util.Arrays.stream(ChatAttachmentService.class.getMethods())
                .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
                .anyMatch(type -> type.getName().equals("org.springframework.web.multipart.MultipartFile"));
        assertFalse(exposesMultipartFile);
    }

    @Test
    void returnsAccessibleDownloadDescription() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        ChatAttachment attachment = new ChatAttachment();
        attachment.setId("A1"); attachment.setSessionId("S1"); attachment.setStoredName("A1.xlsx");
        attachment.setOriginalName("schedule.xlsx"); attachment.setContentType("application/test");
        attachment.setFileSize(123L); attachment.setMessageType("FILE");
        ChatSession session = new ChatSession(); session.setId("S1"); session.setUserId("U1");
        when(attachmentMapper.selectById("A1")).thenReturn(attachment);
        when(sessionMapper.selectById("S1")).thenReturn(session);
        when(storage.open("A1.xlsx")).thenAnswer(invocation -> new ByteArrayInputStream(new byte[]{1}));

        AttachmentDownload download = service(attachmentMapper, sessionMapper, storage)
                .loadAccessibleDownload("U1", "A1");

        assertEquals("schedule.xlsx", download.filename());
        assertEquals("application/test", download.contentType());
        assertEquals(123L, download.size());
        assertFalse(download.inline());
        assertNotNull(download.resource());
    }
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
        ChatAttachmentServiceImpl service = service(attachmentMapper, sessionMapper, storage);

        var result = service.upload("U1", "S1",
                new AttachmentUpload("safe.jpg", new byte[]{
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
        ChatAttachmentServiceImpl service = service(attachmentMapper, sessionMapper, storage);

        assertThrows(IllegalArgumentException.class, () -> service.upload("U1", "S1",
                new AttachmentUpload("payload.jpg", "<script>alert(1)</script>".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.upload("U1", "S1",
                new AttachmentUpload("payload.zip", new byte[]{0x50, 0x4B, 0x03, 0x04})));
    }

    @Test
    void uploadRejectsUnsupportedExtensionAndNonParticipant() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        ChatSession session = new ChatSession(); session.setId("S1"); session.setUserId("U1"); session.setAgentId("A1");
        when(sessionMapper.selectById("S1")).thenReturn(session);
        ChatAttachmentServiceImpl service = service(attachmentMapper, sessionMapper, storage);
        assertThrows(IllegalArgumentException.class, () -> service.upload("X1", "S1",
                new AttachmentUpload("safe.jpg", new byte[]{1})));
        assertThrows(IllegalArgumentException.class, () -> service.upload("U1", "S1",
                new AttachmentUpload("attack.html", new byte[]{1})));
    }

    @Test
    void cleanupDoesNotCountAnObjectWhoseDeletionFails() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ChatAttachment.class
        );
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        when(storage.list("")).thenReturn(List.of(
                new AttachmentObjectStorage.StoredObject("orphan.txt", 1L, Instant.EPOCH)
        ));
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage).delete("orphan.txt");
        ChatAttachmentServiceImpl service = service(attachmentMapper, mock(ChatSessionMapper.class), storage);

        assertEquals(0, service.cleanupOrphanFiles());
    }

    @Test
    void previewsAccessibleLegacyOfficeAttachmentAsPdf() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        OfficePreviewConverter converter = mock(OfficePreviewConverter.class);
        ChatAttachment attachment = new ChatAttachment();
        attachment.setId("A1");
        attachment.setSessionId("S1");
        attachment.setOriginalName("schedule.xls");
        attachment.setStoredName("A1.xls");
        ChatSession session = new ChatSession();
        session.setId("S1");
        session.setUserId("U1");
        when(attachmentMapper.selectById("A1")).thenReturn(attachment);
        when(sessionMapper.selectById("S1")).thenReturn(session);
        when(storage.open("A1.xls")).thenAnswer(invocation -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(converter.convertToPdf(eq("schedule.xls"), any())).thenReturn("%PDF-preview".getBytes());
        ChatAttachmentServiceImpl service = new ChatAttachmentServiceImpl(
                attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties(), converter
        );

        AttachmentPreview preview = service.preview("U1", "A1");
        AttachmentPreview cachedPreview = service.preview("U1", "A1");

        assertEquals("schedule.pdf", preview.filename());
        assertArrayEquals("%PDF-preview".getBytes(), preview.content());
        assertArrayEquals(preview.content(), cachedPreview.content());
        verify(converter, times(1)).convertToPdf(eq("schedule.xls"), any());
    }

    @Test
    void servesPreviewFromPersistedStorageCacheWithoutConversion() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        OfficePreviewConverter converter = mock(OfficePreviewConverter.class);
        ChatAttachment attachment = new ChatAttachment();
        attachment.setId("A1");
        attachment.setSessionId("S1");
        attachment.setOriginalName("schedule.doc");
        attachment.setStoredName("A1.doc");
        ChatSession session = new ChatSession();
        session.setId("S1");
        session.setUserId("U1");
        when(attachmentMapper.selectById("A1")).thenReturn(attachment);
        when(sessionMapper.selectById("S1")).thenReturn(session);
        when(storage.open("previews/A1.pdf"))
                .thenAnswer(invocation -> new ByteArrayInputStream("%PDF-cached".getBytes()));
        ChatAttachmentServiceImpl service = new ChatAttachmentServiceImpl(
                attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties(), converter
        );

        AttachmentPreview preview = service.preview("U1", "A1");

        assertEquals("schedule.pdf", preview.filename());
        assertArrayEquals("%PDF-cached".getBytes(), preview.content());
        verify(converter, never()).convertToPdf(any(), any());
        verify(storage, never()).open("A1.doc");
    }

    @Test
    void persistsConvertedPreviewToStorageCache() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        OfficePreviewConverter converter = mock(OfficePreviewConverter.class);
        ChatAttachment attachment = new ChatAttachment();
        attachment.setId("A1");
        attachment.setSessionId("S1");
        attachment.setOriginalName("slides.pptx");
        attachment.setStoredName("A1.pptx");
        ChatSession session = new ChatSession();
        session.setId("S1");
        session.setUserId("U1");
        when(attachmentMapper.selectById("A1")).thenReturn(attachment);
        when(sessionMapper.selectById("S1")).thenReturn(session);
        when(storage.open("previews/A1.pdf")).thenThrow(new RuntimeException("not found"));
        when(storage.open("A1.pptx")).thenAnswer(invocation -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(converter.convertToPdf(eq("slides.pptx"), any())).thenReturn("%PDF-preview".getBytes());
        ChatAttachmentServiceImpl service = new ChatAttachmentServiceImpl(
                attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties(), converter
        );

        AttachmentPreview preview = service.preview("U1", "A1");

        assertArrayEquals("%PDF-preview".getBytes(), preview.content());
        verify(storage).put(eq("previews/A1.pdf"), any(), eq((long) "%PDF-preview".getBytes().length), eq("application/pdf"));
    }

    @Test
    void returnsOnlyAccessibleAttachmentMetadataInOneBatch() {
        ChatAttachmentMapper attachmentMapper = mock(ChatAttachmentMapper.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        ChatAttachment visible = new ChatAttachment();
        visible.setId("A1"); visible.setSessionId("S1"); visible.setOriginalName("visible.xlsx");
        ChatAttachment hidden = new ChatAttachment();
        hidden.setId("A2"); hidden.setSessionId("S2"); hidden.setOriginalName("hidden.xlsx");
        ChatSession visibleSession = new ChatSession();
        visibleSession.setId("S1"); visibleSession.setUserId("U1");
        ChatSession hiddenSession = new ChatSession();
        hiddenSession.setId("S2"); hiddenSession.setUserId("U2");
        when(attachmentMapper.selectByIds(List.of("A1", "A2"))).thenReturn(List.of(visible, hidden));
        when(sessionMapper.selectByIds(List.of("S1", "S2"))).thenReturn(List.of(visibleSession, hiddenSession));
        ChatAttachmentServiceImpl service = service(attachmentMapper, sessionMapper, storage);

        var result = service.findAccessibleMetadata("U1", List.of("A1", "A2"));

        assertEquals(1, result.size());
        assertEquals("A1", result.get(0).getId());
        verify(attachmentMapper).selectByIds(List.of("A1", "A2"));
        verify(sessionMapper).selectByIds(List.of("S1", "S2"));
    }

    private ChatAttachmentServiceImpl service(ChatAttachmentMapper attachmentMapper,
                                              ChatSessionMapper sessionMapper,
                                              AttachmentObjectStorage storage) {
        return new ChatAttachmentServiceImpl(
                attachmentMapper, sessionMapper, storage, new MinioAttachmentProperties(),
                mock(OfficePreviewConverter.class)
        );
    }
}
