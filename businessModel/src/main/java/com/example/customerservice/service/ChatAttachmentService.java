package com.example.customerservice.service;

import com.example.customerservice.dto.ChatAttachmentVO;
import com.example.customerservice.dto.AttachmentDownload;
import com.example.customerservice.dto.AttachmentUpload;

import java.util.List;

public interface ChatAttachmentService {
    ChatAttachmentVO upload(String userId, String sessionId, AttachmentUpload upload);
    List<ChatAttachmentVO> findAccessibleMetadata(String userId, List<String> attachmentIds);
    AttachmentDownload loadAccessibleDownload(String userId, String attachmentId);
    AttachmentPreview preview(String userId, String attachmentId);
    int cleanupOrphanFiles();
}
