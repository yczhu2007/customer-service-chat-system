package com.example.customerservice.service;

import com.example.customerservice.dto.ChatAttachmentVO;
import com.example.customerservice.dto.AttachmentDownload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatAttachmentService {
    ChatAttachmentVO upload(String userId, String sessionId, MultipartFile file);
    List<ChatAttachmentVO> findAccessibleMetadata(String userId, List<String> attachmentIds);
    AttachmentDownload loadAccessibleDownload(String userId, String attachmentId);
    AttachmentPreview preview(String userId, String attachmentId);
    int cleanupOrphanFiles();
}
