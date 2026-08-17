package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.dto.ChatAttachmentVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatAttachmentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/chat/attachments")
@Validated
public class ChatAttachmentController {
    private final ChatAttachmentService service;
    private final CurrentUser currentUser;
    public ChatAttachmentController(ChatAttachmentService service, CurrentUser currentUser) {
        this.service = service; this.currentUser = currentUser;
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ChatAttachmentVO> upload(
            @RequestParam @NotBlank @Size(max=64) String sessionId,
            @RequestPart("file") MultipartFile file) {
        return Result.success(service.upload(currentUser.getUserId(), sessionId, file));
    }
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable @NotBlank @Size(max=64) String id) {
        ChatAttachment attachment = service.requireAccessible(currentUser.getUserId(), id);
        MediaType type;
        try { type = MediaType.parseMediaType(attachment.getContentType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = "IMAGE".equals(attachment.getMessageType())
                ? ContentDisposition.inline().filename(attachment.getOriginalName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(attachment.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(attachment.getFileSize())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(service.load(attachment));
    }
}
