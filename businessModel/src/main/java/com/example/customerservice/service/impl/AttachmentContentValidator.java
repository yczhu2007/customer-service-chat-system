package com.example.customerservice.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class AttachmentContentValidator {
    private static final long MAX_ZIP_UNCOMPRESSED_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final int MAX_ZIP_COMPRESSION_RATIO = 100;
    private static final Set<String> ZIP_EXTENSIONS = Set.of("zip", "docx", "xlsx", "pptx");

    private AttachmentContentValidator() { }

    static void validate(byte[] content, String extension) {
        if (!matchesMagicNumber(content, extension)) {
            throw new IllegalArgumentException("附件内容与文件扩展名不匹配");
        }
        if (ZIP_EXTENSIONS.contains(extension)) validateZip(content, extension);
    }

    private static boolean matchesMagicNumber(byte[] content, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(content, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(content, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "webp" -> startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && content.length >= 12 && startsWith(content, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "zip", "docx", "xlsx", "pptx" -> startsWith(content, 0x50, 0x4B, 0x03, 0x04);
            case "doc", "xls", "ppt" -> startsWith(content, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "txt" -> isPlainText(content);
            default -> false;
        };
    }

    private static void validateZip(byte[] content, String extension) {
        long uncompressedBytes = 0;
        int entries = 0;
        boolean contentTypes = false;
        boolean officeDirectory = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
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
                        || ("xlsx".equals(extension) && name.startsWith("xl/"))
                        || ("pptx".equals(extension) && name.startsWith("ppt/"));
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressedBytes += read;
                    if (uncompressedBytes > MAX_ZIP_UNCOMPRESSED_SIZE) {
                        throw new IllegalArgumentException("压缩包解压后过大");
                    }
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

    private static boolean isPlainText(byte[] content) {
        for (byte value : content) {
            int character = value & 0xFF;
            if (character == 0 || character < 0x09 || (character > 0x0D && character < 0x20)) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] content, int... expected) {
        if (content.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((content[index] & 0xFF) != expected[index]) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] content, byte[] expected) {
        return startsWith(content, 0, expected);
    }

    private static boolean startsWith(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) return false;
        }
        return true;
    }
}
