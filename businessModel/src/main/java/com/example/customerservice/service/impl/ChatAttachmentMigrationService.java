package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.config.MinioAttachmentProperties;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.storage.AttachmentObjectStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HexFormat;

@Service
public class ChatAttachmentMigrationService {
    private final ChatAttachmentMapper attachmentMapper;
    private final AttachmentObjectStorage storage;
    private final MinioAttachmentProperties properties;

    public ChatAttachmentMigrationService(ChatAttachmentMapper attachmentMapper,
                                          AttachmentObjectStorage storage,
                                          MinioAttachmentProperties properties) {
        this.attachmentMapper = attachmentMapper;
        this.storage = storage;
        this.properties = properties;
    }

    public int migrate() {
        Path legacyRoot = properties.getMigration().getLegacyStoragePath().toAbsolutePath().normalize();
        List<ChatAttachment> attachments = attachmentMapper.selectList(Wrappers.emptyWrapper());
        int migrated = 0;
        for (ChatAttachment attachment : attachments) {
            migrated += migrateOne(legacyRoot, attachment);
        }
        assertNoUnreferencedFiles(legacyRoot, attachments);
        removeLegacyDirectory(legacyRoot);
        return migrated;
    }

    private int migrateOne(Path legacyRoot, ChatAttachment attachment) {
        String storedName = attachment.getStoredName();
        if (storedName == null || storedName.isBlank() || storedName.contains("..")
                || Path.of(storedName).isAbsolute()) {
            throw new IllegalStateException("附件记录包含不安全的存储名称: " + attachment.getId());
        }
        Path source = legacyRoot.resolve(storedName).normalize();
        if (!source.startsWith(legacyRoot)) throw new IllegalStateException("附件路径越界: " + attachment.getId());
        try {
            if (Files.isRegularFile(source)) {
                long localSize = Files.size(source);
                if (attachment.getFileSize() != null && localSize != attachment.getFileSize()) {
                    throw new IllegalStateException("附件大小校验失败: " + attachment.getId());
                }
                String checksum = checksum(source);
                try (InputStream input = Files.newInputStream(source)) {
                    storage.put(storedName, input, localSize, attachment.getContentType());
                }
                verifyObject(storedName, localSize, checksum);
                return 1;
            }
            verifySize(storedName, attachment.getFileSize());
            return 0;
        } catch (IOException exception) {
            throw new IllegalStateException("历史附件迁移失败: " + attachment.getId(), exception);
        }
    }

    private void verifySize(String objectName, long expectedSize) {
        long actualSize = storage.stat(objectName).size();
        if (actualSize != expectedSize) {
            throw new IllegalStateException("附件校验失败: " + objectName);
        }
    }

    private void verifyObject(String objectName, long expectedSize, String expectedChecksum) {
        verifySize(objectName, expectedSize);
        try (InputStream input = storage.open(objectName)) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            if (!expectedChecksum.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IllegalStateException("附件内容校验失败: " + objectName);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("附件回读校验失败: " + objectName, exception);
        }
    }

    private String checksum(Path source) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("系统不支持 SHA-256", exception);
        }
    }

    private void assertNoUnreferencedFiles(Path legacyRoot, List<ChatAttachment> attachments) {
        try {
            if (Files.isDirectory(legacyRoot)) {
                var referenced = attachments.stream().map(ChatAttachment::getStoredName).filter(name -> name != null).toList();
                try (var entries = Files.list(legacyRoot)) {
                    if (entries.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .anyMatch(name -> !referenced.contains(name))) {
                        throw new IllegalStateException("本地附件目录存在未登记文件，已停止删除本地目录");
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("历史附件目录校验失败", exception);
        }
    }

    private void removeLegacyDirectory(Path legacyRoot) {
        if (!Files.isDirectory(legacyRoot)) return;
        Path pending = legacyRoot.resolveSibling(legacyRoot.getFileName() + ".pending-delete");
        try {
            if (Files.exists(pending)) throw new IllegalStateException("存在上一次迁移遗留的待删除目录: " + pending.getFileName());
            try { Files.move(legacyRoot, pending, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException exception) { Files.move(legacyRoot, pending); }
            try (var entries = Files.walk(pending)) {
                entries.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException exception) { throw new IllegalStateException("历史附件目录清理失败", exception); }
                });
            }
        } catch (IOException exception) {
            throw new IllegalStateException("历史附件目录校验失败", exception);
        }
    }
}
