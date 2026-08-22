package com.example.customerservice.service.impl;

import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.ChatAttachmentVO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.service.ChatAttachmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ChatAttachmentServiceImpl implements ChatAttachmentService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final long MAX_ZIP_UNCOMPRESSED_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final int MAX_ZIP_COMPRESSION_RATIO = 100;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "doc", "docx", "xls", "xlsx", "zip"
    );
    private static final java.util.Map<String, String> CONTENT_TYPES = java.util.Map.ofEntries(
            java.util.Map.entry("jpg", "image/jpeg"), java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("png", "image/png"), java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("webp", "image/webp"), java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("txt", "text/plain"), java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("zip", "application/zip")
    );
    private final ChatAttachmentMapper attachmentMapper;
    private final ChatSessionMapper sessionMapper;
    private final Path storageRoot;

    public ChatAttachmentServiceImpl(ChatAttachmentMapper attachmentMapper, ChatSessionMapper sessionMapper,
                                     @Value("${app.chat.attachment.storage-path:./data/chat-attachments}") String storagePath) {
        this.attachmentMapper = attachmentMapper;
        this.sessionMapper = sessionMapper;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public ChatAttachmentVO upload(String userId, String sessionId, MultipartFile file) {
        requireParticipant(userId, sessionId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择需要上传的附件");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("附件大小不能超过10MB");
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        if (originalName.contains("..")) throw new IllegalArgumentException("附件名称不合法");
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("不支持该附件格式");
        validateActualType(file, extension);

        String id = UUID.randomUUID().toString().replace("-", "");
        String storedName = id + "." + extension;
        Path destination = storageRoot.resolve(storedName).normalize();
        if (!destination.startsWith(storageRoot)) throw new IllegalArgumentException("附件存储路径不合法");
        try {
            Files.createDirectories(storageRoot);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("附件保存失败", exception);
        }
        registerRollbackCleanup(destination);

        try {
            ChatAttachment attachment = new ChatAttachment();
            attachment.setId(id); attachment.setSessionId(sessionId); attachment.setUploaderId(userId);
            attachment.setOriginalName(originalName); attachment.setStoredName(storedName);
            attachment.setContentType(CONTENT_TYPES.get(extension));
            attachment.setFileSize(file.getSize());
            attachment.setMessageType(Set.of("jpg", "jpeg", "png", "gif", "webp").contains(extension) ? "IMAGE" : "FILE");
            if (attachmentMapper.insert(attachment) != 1) throw new IllegalStateException("附件记录保存失败");
            ChatAttachment saved = attachmentMapper.selectById(id);
            if (saved == null) throw new IllegalStateException("附件记录读取失败");
            return toVO(saved);
        } catch (RuntimeException exception) {
            deleteQuietly(destination);
            throw exception;
        }
    }

    private void validateActualType(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] content = input.readAllBytes();
            if (!matchesMagicNumber(content, extension)) throw new IllegalArgumentException("附件内容与文件扩展名不匹配");
            if (Set.of("zip", "docx", "xlsx").contains(extension)) validateZip(content, extension);
        } catch (IOException exception) {
            throw new IllegalStateException("附件读取失败", exception);
        }
    }

    private boolean matchesMagicNumber(byte[] content, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(content, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(content, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "webp" -> startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && content.length >= 12 && startsWith(content, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "zip", "docx", "xlsx" -> startsWith(content, 0x50, 0x4B, 0x03, 0x04);
            case "doc", "xls" -> startsWith(content, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "txt" -> isPlainText(content);
            default -> false;
        };
    }

    private void validateZip(byte[] content, String extension) {
        long uncompressedBytes = 0;
        int entries = 0;
        boolean contentTypes = false;
        boolean officeDirectory = false;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES || entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..") || name.indexOf('\\') >= 0) {
                    throw new IllegalArgumentException("压缩包包含不安全路径");
                }
                contentTypes |= "[Content_Types].xml".equals(name);
                officeDirectory |= ("docx".equals(extension) && name.startsWith("word/"))
                        || ("xlsx".equals(extension) && name.startsWith("xl/"));
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressedBytes += read;
                    if (uncompressedBytes > MAX_ZIP_UNCOMPRESSED_SIZE) throw new IllegalArgumentException("压缩包解压后过大");
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("压缩包格式无效");
        }
        if (entries == 0 || entries > MAX_ZIP_ENTRIES) throw new IllegalArgumentException("压缩包条目数量异常");
        if (content.length > 0 && uncompressedBytes / content.length > MAX_ZIP_COMPRESSION_RATIO) {
            throw new IllegalArgumentException("压缩包压缩比例异常");
        }
        if (!"zip".equals(extension) && (!contentTypes || !officeDirectory)) {
            throw new IllegalArgumentException("Office 文件内容与扩展名不匹配");
        }
    }

    private boolean isPlainText(byte[] content) {
        for (byte value : content) {
            int c = value & 0xFF;
            if (c == 0 || (c < 0x09) || (c > 0x0D && c < 0x20)) return false;
        }
        return true;
    }

    private boolean startsWith(byte[] content, int... expected) {
        if (content.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((content[i] & 0xFF) != expected[i]) return false;
        return true;
    }

    private boolean startsWith(byte[] content, byte[] expected) { return startsWith(content, 0, expected); }
    private boolean startsWith(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (content[offset + i] != expected[i]) return false;
        return true;
    }

    private void registerRollbackCleanup(Path destination) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) deleteQuietly(destination);
                }
            });
        }
    }

    private void deleteQuietly(Path destination) {
        try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
    }

    @Override
    public ChatAttachment requireAccessible(String userId, String attachmentId) {
        ChatAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) throw new NotFoundException("附件不存在");
        requireParticipant(userId, attachment.getSessionId());
        return attachment;
    }

    @Override
    public Resource load(ChatAttachment attachment) {
        Path file = storageRoot.resolve(attachment.getStoredName()).normalize();
        if (!file.startsWith(storageRoot) || !Files.isRegularFile(file)) throw new NotFoundException("附件文件不存在");
        return new FileSystemResource(file);
    }

    @Override
    public int cleanupOrphanFiles() {
        if (!Files.isDirectory(storageRoot)) return 0;
        int removed = 0;
        try (Stream<Path> paths = Files.list(storageRoot)) {
            List<Path> batch = new ArrayList<>(200);
            java.util.Iterator<Path> iterator = paths.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                batch.add(iterator.next());
                if (batch.size() == 200) { removed += removeOrphanBatch(batch); batch.clear(); }
            }
            removed += removeOrphanBatch(batch);
        } catch (IOException exception) { throw new IllegalStateException("孤儿附件清理失败", exception); }
        return removed;
    }

    private int removeOrphanBatch(List<Path> paths) throws IOException {
        if (paths.isEmpty()) return 0;
        List<String> storedNames = paths.stream().map(path -> path.getFileName().toString()).toList();
        Set<String> referencedNames = new HashSet<>();
        attachmentMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatAttachment>lambdaQuery()
                        .select(ChatAttachment::getStoredName).in(ChatAttachment::getStoredName, storedNames))
                .forEach(value -> referencedNames.add(value.getStoredName()));
        int removed = 0;
        for (Path path : paths) if (!referencedNames.contains(path.getFileName().toString()) && Files.deleteIfExists(path)) removed++;
        return removed;
    }

    private void requireParticipant(String userId, String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new NotFoundException("聊天会话不存在");
        if (!userId.equals(session.getUserId()) && !userId.equals(session.getAgentId())) throw new IllegalArgumentException("无权访问该会话附件");
    }
    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private ChatAttachmentVO toVO(ChatAttachment value) {
        ChatAttachmentVO vo = new ChatAttachmentVO();
        vo.setId(value.getId()); vo.setSessionId(value.getSessionId()); vo.setOriginalName(value.getOriginalName());
        vo.setContentType(value.getContentType()); vo.setFileSize(value.getFileSize()); vo.setMessageType(value.getMessageType());
        vo.setContentUrl("/chat/attachments/" + value.getId() + "/content"); vo.setCreateTime(value.getCreateTime());
        return vo;
    }
}
