package com.example.customerservice.service;

import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.dto.ChatAttachmentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ChatAttachmentService {
    ChatAttachmentVO upload(String userId, String sessionId, MultipartFile file);
    ChatAttachment requireAccessible(String userId, String attachmentId);
    Resource load(ChatAttachment attachment);
    AttachmentPreview preview(String userId, String attachmentId);
    int cleanupOrphanFiles();
}
