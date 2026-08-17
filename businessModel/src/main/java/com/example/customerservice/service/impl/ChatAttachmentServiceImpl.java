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
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.HashSet;

@Service
public class ChatAttachmentServiceImpl implements ChatAttachmentService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
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
        String id = UUID.randomUUID().toString().replace("-", "");
        String storedName = id + "." + extension;
        Path destination = storageRoot.resolve(storedName).normalize();
        if (!destination.startsWith(storageRoot)) throw new IllegalArgumentException("附件存储路径不合法");
        try {
            Files.createDirectories(storageRoot);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("附件保存失败", exception);
        }
        ChatAttachment attachment = new ChatAttachment();
        attachment.setId(id); attachment.setSessionId(sessionId); attachment.setUploaderId(userId);
        attachment.setOriginalName(originalName); attachment.setStoredName(storedName);
        // MIME类型只由服务端扩展名白名单决定，不信任客户端上传的Content-Type。
        attachment.setContentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
        attachment.setFileSize(file.getSize());
        attachment.setMessageType(Set.of("jpg", "jpeg", "png", "gif", "webp").contains(extension) ? "IMAGE" : "FILE");
        try {
            if (attachmentMapper.insert(attachment) != 1) {
                throw new IllegalStateException("附件记录保存失败");
            }
        } catch (RuntimeException exception) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            throw exception;
        }
        return toVO(attachmentMapper.selectById(id));
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
                if (batch.size() == 200) {
                    removed += removeOrphanBatch(batch);
                    batch.clear();
                }
            }
            removed += removeOrphanBatch(batch);
        } catch (IOException exception) {
            throw new IllegalStateException("孤儿附件清理失败", exception);
        }
        return removed;
    }

    private int removeOrphanBatch(List<Path> paths) throws IOException {
        if (paths.isEmpty()) return 0;
        List<String> storedNames = paths.stream()
                .map(path -> path.getFileName().toString())
                .toList();
        Set<String> referencedNames = new HashSet<>();
        attachmentMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatAttachment>lambdaQuery()
                                .select(ChatAttachment::getStoredName)
                                .in(ChatAttachment::getStoredName, storedNames)
                )
                .forEach(value -> referencedNames.add(value.getStoredName()));
        int removed = 0;
        for (Path path : paths) {
            if (!referencedNames.contains(path.getFileName().toString()) && Files.deleteIfExists(path)) {
                removed++;
            }
        }
        return removed;
    }

    private void requireParticipant(String userId, String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new NotFoundException("聊天会话不存在");
        if (!userId.equals(session.getUserId()) && !userId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("无权访问该会话附件");
        }
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
