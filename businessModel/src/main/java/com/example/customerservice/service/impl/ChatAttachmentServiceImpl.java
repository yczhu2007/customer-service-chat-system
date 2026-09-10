package com.example.customerservice.service.impl;

import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.ChatAttachmentVO;
import com.example.customerservice.dto.AttachmentDownload;
import com.example.customerservice.dto.AttachmentUpload;
import com.example.customerservice.constant.ChatMessageType;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.service.ChatAttachmentService;
import com.example.customerservice.service.AttachmentPreview;
import com.example.customerservice.service.OfficePreviewConverter;
import com.example.customerservice.storage.AttachmentObjectStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ChatAttachmentServiceImpl implements ChatAttachmentService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final int CLEANUP_QUERY_BATCH_SIZE = 500;
    private static final int PREVIEW_CACHE_SIZE = 4;
    /** 预览转换结果在对象存储中的持久缓存前缀。 */
    private static final String PREVIEW_OBJECT_PREFIX = "previews/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip"
    );
    private static final java.util.Map<String, String> CONTENT_TYPES = java.util.Map.ofEntries(
            java.util.Map.entry("jpg", "image/jpeg"), java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("png", "image/png"), java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("webp", "image/webp"), java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("txt", "text/plain"), java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("ppt", "application/vnd.ms-powerpoint"),
            java.util.Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            java.util.Map.entry("zip", "application/zip")
    );
    private final ChatAttachmentMapper attachmentMapper;
    private final ChatSessionMapper sessionMapper;
    private final AttachmentObjectStorage storage;
    private final MinioAttachmentProperties properties;
    private final OfficePreviewConverter previewConverter;
    private final Map<String, AttachmentPreview> previewCache = Collections.synchronizedMap(
            new LinkedHashMap<>(PREVIEW_CACHE_SIZE + 1, 1.0f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AttachmentPreview> eldest) {
                    return size() > PREVIEW_CACHE_SIZE;
                }
            }
    );

    @Autowired
    public ChatAttachmentServiceImpl(ChatAttachmentMapper attachmentMapper, ChatSessionMapper sessionMapper,
                                     AttachmentObjectStorage storage,
                                     MinioAttachmentProperties properties,
                                     OfficePreviewConverter previewConverter) {
        this.attachmentMapper = attachmentMapper;
        this.sessionMapper = sessionMapper;
        this.storage = storage;
        this.properties = properties;
        this.previewConverter = previewConverter;
    }

    @Override
    @Transactional
    public ChatAttachmentVO upload(String userId, String sessionId, AttachmentUpload upload) {
        requireParticipant(userId, sessionId);
        if (upload == null || upload.size() == 0) throw new IllegalArgumentException("请选择需要上传的附件");
        if (upload.size() > MAX_SIZE) throw new IllegalArgumentException("附件大小不能超过10MB");
        String originalName = StringUtils.cleanPath(upload.filename() == null ? "attachment" : upload.filename());
        if (originalName.contains("..")) throw new IllegalArgumentException("附件名称不合法");
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("不支持该附件格式");
        byte[] content = upload.content();
        AttachmentContentValidator.validate(content, extension);

        String id = UUID.randomUUID().toString().replace("-", "");
        String storedName = id + "." + extension;
        storage.put(storedName, new java.io.ByteArrayInputStream(content), upload.size(), CONTENT_TYPES.get(extension));
        registerRollbackCleanup(storedName);

        try {
            ChatAttachment attachment = new ChatAttachment();
            attachment.setId(id); attachment.setSessionId(sessionId); attachment.setUploaderId(userId);
            attachment.setOriginalName(originalName); attachment.setStoredName(storedName);
            attachment.setContentType(CONTENT_TYPES.get(extension));
            attachment.setFileSize(upload.size());
            attachment.setMessageType(Set.of("jpg", "jpeg", "png", "gif", "webp").contains(extension)
                    ? ChatMessageType.IMAGE.name() : ChatMessageType.FILE.name());
            if (attachmentMapper.insert(attachment) != 1) throw new IllegalStateException("附件记录保存失败");
            ChatAttachment saved = attachmentMapper.selectById(id);
            if (saved == null) throw new IllegalStateException("附件记录读取失败");
            return toVO(saved);
        } catch (RuntimeException exception) {
            deleteQuietly(storedName);
            throw exception;
        }
    }

    private void registerRollbackCleanup(String objectName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) deleteQuietly(objectName);
                }
            });
        }
    }

    private boolean deleteQuietly(String objectName) {
        try {
            storage.delete(objectName);
            return true;
        } catch (RuntimeException exception) {
            log.warn("删除附件对象失败，objectName={}", objectName, exception);
            return false;
        }
    }

    private ChatAttachment requireAccessible(String userId, String attachmentId) {
        ChatAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) throw new NotFoundException("附件不存在");
        requireParticipant(userId, attachment.getSessionId());
        return attachment;
    }

    @Override
    public List<ChatAttachmentVO> findAccessibleMetadata(String userId, List<String> attachmentIds) {
        List<String> ids = attachmentIds.stream().filter(StringUtils::hasText).distinct().limit(50).toList();
        if (ids.isEmpty()) return List.of();
        List<ChatAttachment> attachments = attachmentMapper.selectByIds(ids);
        if (attachments.isEmpty()) return List.of();
        Map<String, ChatSession> sessions = new java.util.HashMap<>();
        sessionMapper.selectByIds(attachments.stream().map(ChatAttachment::getSessionId).distinct().toList())
                .forEach(session -> sessions.put(session.getId(), session));
        return attachments.stream()
                .filter(attachment -> {
                    ChatSession session = sessions.get(attachment.getSessionId());
                    return session != null && (userId.equals(session.getUserId()) || userId.equals(session.getAgentId()));
                })
                .map(this::toVO)
                .toList();
    }

    private Resource load(ChatAttachment attachment) {
        try {
            return new InputStreamResource(storage.open(attachment.getStoredName()));
        } catch (RuntimeException exception) {
            throw new NotFoundException("附件文件不存在");
        }
    }

    @Override
    public AttachmentDownload loadAccessibleDownload(String userId, String attachmentId) {
        ChatAttachment attachment = requireAccessible(userId, attachmentId);
        return new AttachmentDownload(attachment.getOriginalName(), attachment.getContentType(),
                attachment.getFileSize(), ChatMessageType.IMAGE.name().equals(attachment.getMessageType()), load(attachment));
    }

    @Override
    public AttachmentPreview preview(String userId, String attachmentId) {
        ChatAttachment attachment = requireAccessible(userId, attachmentId);
        String extension = extensionOf(attachment.getOriginalName());
        if (!Set.of("doc", "xls", "ppt", "pptx").contains(extension)) {
            throw new IllegalArgumentException("该附件不支持服务端预览");
        }
        AttachmentPreview cachedPreview = previewCache.get(attachmentId);
        if (cachedPreview != null) return cachedPreview;
        String baseName = attachment.getOriginalName().substring(
                0, attachment.getOriginalName().length() - extension.length() - 1
        );
        String previewFileName = baseName + ".pdf";
        String previewObjectName = PREVIEW_OBJECT_PREFIX + attachmentId + ".pdf";

        /*
         * L2 持久缓存：转换结果按附件ID存入对象存储。
         * 附件不可变，转换结果可跨实例、跨重启复用，命中即免掉 LibreOffice 转换。
         */
        byte[] persistedContent = readPersistedPreview(previewObjectName);
        if (persistedContent != null) {
            AttachmentPreview preview = new AttachmentPreview(previewFileName, persistedContent);
            previewCache.put(attachmentId, preview);
            return preview;
        }

        InputStream storedInput;
        try {
            storedInput = storage.open(attachment.getStoredName());
        } catch (RuntimeException exception) {
            throw new NotFoundException("附件文件不存在");
        }
        try (InputStream input = storedInput) {
            byte[] content = previewConverter.convertToPdf(attachment.getOriginalName(), input);
            AttachmentPreview preview = new AttachmentPreview(previewFileName, content);
            previewCache.put(attachmentId, preview);
            writePersistedPreview(previewObjectName, content);
            return preview;
        } catch (IOException exception) {
            throw new IllegalStateException("附件读取失败", exception);
        }
    }

    private byte[] readPersistedPreview(String previewObjectName) {
        try (InputStream input = storage.open(previewObjectName)) {
            if (input == null) return null;
            return input.readAllBytes();
        } catch (IOException exception) {
            log.warn("读取预览缓存内容失败，转为实时转换，object={}", previewObjectName, exception);
            return null;
        } catch (RuntimeException exception) {
            /* 对象不存在（缓存未命中）或存储暂不可用，都按未命中处理。 */
            log.debug("预览缓存未命中，object={}", previewObjectName, exception);
            return null;
        }
    }

    private void writePersistedPreview(String previewObjectName, byte[] content) {
        try {
            storage.put(previewObjectName, new java.io.ByteArrayInputStream(content), content.length, "application/pdf");
        } catch (RuntimeException exception) {
            /* 缓存写失败不影响本次预览，下次请求重新转换即可。 */
            log.warn("预览结果写入持久缓存失败，object={}", previewObjectName, exception);
        }
    }

    @Override
    public int cleanupOrphanFiles() {
        List<AttachmentObjectStorage.StoredObject> objects = storage.list("");
        if (objects.isEmpty()) return 0;
        List<String> storedNames = objects.stream()
                .map(AttachmentObjectStorage.StoredObject::objectName)
                .toList();
        Set<String> referencedNames = new HashSet<>();
        for (int start = 0; start < storedNames.size(); start += CLEANUP_QUERY_BATCH_SIZE) {
            List<String> batch = new ArrayList<>(storedNames.subList(
                    start,
                    Math.min(start + CLEANUP_QUERY_BATCH_SIZE, storedNames.size())
            ));
            attachmentMapper.selectList(
                            com.baomidou.mybatisplus.core.toolkit.Wrappers
                                    .<ChatAttachment>lambdaQuery()
                                    .select(ChatAttachment::getStoredName)
                                    .in(ChatAttachment::getStoredName, batch)
                    )
                    .forEach(value -> referencedNames.add(value.getStoredName()));
        }
        /* 预览缓存对象（previews/{附件ID}.pdf）是否保留取决于附件本体是否仍存在。 */
        Set<String> referencedAttachmentIds = new HashSet<>();
        for (String referencedName : referencedNames) {
            int dotIndex = referencedName.lastIndexOf('.');
            referencedAttachmentIds.add(dotIndex > 0 ? referencedName.substring(0, dotIndex) : referencedName);
        }
        int removed = 0;
        Instant cutoff = Instant.now().minusSeconds(properties.getOrphanGracePeriodSeconds());
        for (AttachmentObjectStorage.StoredObject object : objects) {
            String objectName = object.objectName();
            if (object.lastModified() != null && object.lastModified().isAfter(cutoff)) continue;
            if (objectName.startsWith(PREVIEW_OBJECT_PREFIX)) {
                String attachmentId = objectName.substring(
                        PREVIEW_OBJECT_PREFIX.length(),
                        objectName.length() - ".pdf".length()
                );
                if (!referencedAttachmentIds.contains(attachmentId) && deleteQuietly(objectName)) {
                    removed++;
                }
                continue;
            }
            if (!referencedNames.contains(objectName) && deleteQuietly(objectName)) {
                removed++;
            }
        }
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
