package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.dto.ArchiveStatsVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatSessionMetadataUpdateDTO;
import com.example.customerservice.dto.ChatSessionMetadataVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.QueueStatusVO;
import com.example.customerservice.dto.SessionArchiveDTO;
import com.example.customerservice.dto.SessionArchiveRemarkDTO;
import com.example.customerservice.dto.SessionRatingVO;
import com.example.customerservice.dto.SessionRatingDTO;
import com.example.customerservice.dto.UserProfileSidebarVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatSessionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/chat")
@Validated
public class ChatSessionController {

    @Autowired private CurrentUser currentUser;
    @Autowired private ChatSessionQueryService chatSessionQueryService;
    @Autowired private HttpServletRequest httpServletRequest;

    @GetMapping("/sessions")
    public Result<PageResult<ChatSessionListItemVO>> findMySessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String archiveStatus,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNo,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能超过100") long pageSize
    ) {
        currentUser.requirePermission("chat:session:view-own");
        return Result.success(chatSessionQueryService.findMySessions(
                currentUser.getUserId(), resolveSessionParticipantType(), status, archiveStatus, pageNo, pageSize));
    }

    @GetMapping("/sessions/{sessionId}/rating")
    public Result<SessionRatingVO> getSessionRating(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        currentUser.requireRole("USER");
        return Result.success(chatSessionQueryService.getSessionRating(currentUser.getUserId(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/rating")
    public Result<SessionRatingVO> rateSession(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SessionRatingDTO request
    ) {
        currentUser.requireRole("USER");
        currentUser.requirePermission("chat:session:rate");
        return Result.success(chatSessionQueryService.rateSession(currentUser.getUserId(), sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/user-profile")
    public Result<UserProfileSidebarVO> getUserProfileSidebar(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:user-profile:view");
        return Result.success(chatSessionQueryService.getUserProfileSidebar(currentUser.getUserId(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/metadata")
    public Result<ChatSessionMetadataVO> getSessionMetadata(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        String actorId = currentUser.getUserId();
        String workspaceRole = resolveWorkspaceRole("AGENT", "USER", "ADMIN");
        if ("ADMIN".equals(workspaceRole)) {
            currentUser.requireRole("ADMIN");
            currentUser.requirePermission("chat:session:audit:view");
            return Result.success(chatSessionQueryService.getSessionMetadata(actorId, true, sessionId));
        }
        currentUser.requirePermission("chat:session:view-own");
        return Result.success(chatSessionQueryService.getSessionMetadata(actorId, false, sessionId));
    }

    @PutMapping("/sessions/{sessionId}/metadata")
    public Result<ChatSessionMetadataVO> updateSessionMetadata(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody ChatSessionMetadataUpdateDTO request
    ) {
        resolveWorkspaceRole("AGENT");
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:metadata:update");
        return Result.success(chatSessionQueryService.updateSessionMetadata(currentUser.getUserId(), sessionId, request));
    }

    @GetMapping("/queue-status")
    public Result<QueueStatusVO> getQueueStatus() {
        return Result.success(chatSessionQueryService.getQueueStatus(currentUser.getUserId()));
    }

    @PutMapping("/sessions/{sessionId}/archive-status")
    public Result<Void> setArchiveStatus(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SessionArchiveDTO request
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:archive");
        chatSessionQueryService.setArchiveStatus(currentUser.getUserId(), sessionId, request);
        return Result.successMessage("归档状态更新成功");
    }

    @PutMapping("/sessions/{sessionId}/archive-remark")
    public Result<Void> saveArchiveRemark(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SessionArchiveRemarkDTO request
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:archive");
        chatSessionQueryService.saveArchiveRemark(currentUser.getUserId(), sessionId, request);
        return Result.successMessage("归档备注保存成功");
    }

    @GetMapping("/admin/archive-stats")
    public Result<ArchiveStatsVO> findArchiveStats() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:archive:stats");
        return Result.success(chatSessionQueryService.findArchiveStats());
    }

    private SessionParticipantType resolveSessionParticipantType() {
        return SessionParticipantType.valueOf(resolveWorkspaceRole("USER", "AGENT"));
    }

    private String resolveWorkspaceRole(String... allowedRoles) {
        Set<String> roleCodes = currentUser.getRoleCodes();
        String workspaceRole = httpServletRequest == null ? null : httpServletRequest.getHeader("X-Workspace-Role");
        if (workspaceRole != null && !workspaceRole.isBlank()) {
            for (String allowedRole : allowedRoles) {
                if (allowedRole.equals(workspaceRole) && roleCodes != null && roleCodes.contains(workspaceRole)) {
                    return workspaceRole;
                }
            }
            throw new IllegalArgumentException("当前工作台角色无效");
        }
        if (roleCodes != null) {
            for (String allowedRole : allowedRoles) {
                if (roleCodes.contains(allowedRole)) {
                    return allowedRole;
                }
            }
        }
        throw new IllegalArgumentException("当前工作台没有执行此操作所需角色");
    }
}
