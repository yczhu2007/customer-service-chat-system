package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.AttachmentDownload;
import com.example.customerservice.dto.ChatAttachmentVO;
import com.example.customerservice.dto.AttachmentUpload;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatAttachmentService;
import com.example.customerservice.service.AttachmentPreview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

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
            @RequestPart("file") MultipartFile file) throws IOException {
        return Result.success(service.upload(
                currentUser.getUserId(),
                sessionId,
                new AttachmentUpload(file.getOriginalFilename(), file.getBytes())
        ));
    }
    @PostMapping("/metadata")
    public Result<List<ChatAttachmentVO>> metadata(
            @RequestBody @Size(min = 1, max = 50) List<@Pattern(regexp = "[A-Za-z0-9]{32,64}") String> ids) {
        return Result.success(service.findAccessibleMetadata(currentUser.getUserId(), ids));
    }
    @GetMapping("/{id}")
    public Result<ChatAttachmentVO> metadata(@PathVariable @NotBlank @Size(max=64) String id) {
        List<ChatAttachmentVO> results = service.findAccessibleMetadata(currentUser.getUserId(), List.of(id));
        if (results.isEmpty()) throw new IllegalArgumentException("附件不存在或无权访问");
        return Result.success(results.get(0));
    }
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable @NotBlank @Size(max=64) String id) {
        AttachmentDownload download = service.loadAccessibleDownload(currentUser.getUserId(), id);
        MediaType type;
        try { type = MediaType.parseMediaType(download.contentType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = download.inline()
                ? ContentDisposition.inline().filename(download.filename(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(download.size())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable @NotBlank @Size(max=64) String id) {
        AttachmentPreview preview = service.preview(currentUser.getUserId(), id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(preview.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(preview.content().length)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new ByteArrayResource(preview.content()));
    }
}
