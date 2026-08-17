package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.QuickReplySaveDTO;
import com.example.customerservice.dto.QuickReplyVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.QuickReplyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat/quick-replies")
@Validated
public class QuickReplyController {
    private final QuickReplyService service;
    private final CurrentUser currentUser;
    public QuickReplyController(QuickReplyService service, CurrentUser currentUser) {
        this.service = service; this.currentUser = currentUser;
    }
    @GetMapping
    public Result<List<QuickReplyVO>> findMine() { requireAgent(); return Result.success(service.findMine(currentUser.getUserId())); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<QuickReplyVO> create(@Valid @RequestBody QuickReplySaveDTO request) {
        requireAgent(); return Result.success(HttpStatus.CREATED.value(), "快捷回复创建成功", service.create(currentUser.getUserId(), request));
    }
    @PutMapping("/{id}")
    public Result<QuickReplyVO> update(@PathVariable @NotBlank @Size(max=64) String id, @Valid @RequestBody QuickReplySaveDTO request) {
        requireAgent(); return Result.success(service.update(currentUser.getUserId(), id, request));
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotBlank @Size(max=64) String id) {
        requireAgent(); service.delete(currentUser.getUserId(), id); return Result.successMessage("快捷回复删除成功");
    }
    private void requireAgent() { currentUser.requireRole("AGENT"); currentUser.requirePermission("chat:quick-reply:manage"); }
}
