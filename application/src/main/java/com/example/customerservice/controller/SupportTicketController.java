package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.AdminSupportTicketListItemVO;
import com.example.customerservice.dto.AdminSupportTicketQueryDTO;
import com.example.customerservice.dto.SupportTicketStatusCountVO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketUserFeedbackDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.dto.SupportTicketStatusHistoryVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.security.WorkspaceRoleResolver;
import com.example.customerservice.service.SupportTicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/** 会话关联工单接口。 */
@RestController
@RequestMapping("/chat")
@Validated
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final CurrentUser currentUser;
    private final HttpServletRequest httpServletRequest;
    private final WorkspaceRoleResolver workspaceRoleResolver;

    public SupportTicketController(
            SupportTicketService supportTicketService,
            CurrentUser currentUser,
            HttpServletRequest httpServletRequest,
            WorkspaceRoleResolver workspaceRoleResolver
    ) {
        this.supportTicketService = supportTicketService;
        this.currentUser = currentUser;
        this.httpServletRequest = httpServletRequest;
        this.workspaceRoleResolver = workspaceRoleResolver;
    }

    @GetMapping("/sessions/{sessionId}/ticket")
    public Result<SupportTicketVO> findTicket(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        boolean administrator = isAdministratorWorkspace();
        return Result.success(supportTicketService.findBySessionId(
                currentUser.getUserId(), administrator, sessionId
        ));
    }

    @GetMapping("/sessions/{sessionId}/ticket/history")
    public Result<List<SupportTicketStatusHistoryVO>> findTicketHistory(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        boolean administrator = isAdministratorWorkspace();
        return Result.success(supportTicketService.findHistoryBySessionId(
                currentUser.getUserId(), administrator, sessionId
        ));
    }

    @PostMapping("/sessions/{sessionId}/ticket")
    public Result<SupportTicketVO> createTicket(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SupportTicketCreateDTO request
    ) {
        currentUser.requireRole(ChatConstants.ROLE_AGENT);
        return Result.success(supportTicketService.createTicket(
                currentUser.getUserId(), sessionId, request
        ));
    }

    @PatchMapping("/tickets/{ticketNo}")
    public Result<SupportTicketVO> updateTicket(
            @PathVariable @Pattern(regexp = "TK-\\d{8}") String ticketNo,
            @Valid @RequestBody SupportTicketUpdateDTO request
    ) {
        currentUser.requireRole(ChatConstants.ROLE_AGENT);
        return Result.success(supportTicketService.updateTicket(
                currentUser.getUserId(), ticketNo, request
        ));
    }

    @GetMapping("/sessions/{sessionId}/ticket/history/page")
    public Result<PageResult<SupportTicketStatusHistoryVO>> findTicketHistoryPage(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") long pageNo,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") long pageSize
    ) {
        return Result.success(supportTicketService.findHistoryPageBySessionId(
                currentUser.getUserId(), isAdministratorWorkspace(), sessionId, pageNo, pageSize));
    }

    @GetMapping("/admin/tickets")
    public Result<PageResult<AdminSupportTicketListItemVO>> findAdminTickets(
            @Valid @org.springframework.web.bind.annotation.ModelAttribute AdminSupportTicketQueryDTO query
    ) {
        currentUser.requireRole(ChatConstants.ROLE_ADMIN);
        return Result.success(supportTicketService.findAdminTickets(query));
    }

    @GetMapping("/admin/tickets/status-counts")
    public Result<List<SupportTicketStatusCountVO>> findAdminTicketStatusCounts(
            @Valid @org.springframework.web.bind.annotation.ModelAttribute AdminSupportTicketQueryDTO query
    ) {
        currentUser.requireRole(ChatConstants.ROLE_ADMIN);
        return Result.success(supportTicketService.findAdminTicketStatusCounts(query));
    }

    @PostMapping("/tickets/{ticketNo}/user-feedback")
    public Result<SupportTicketVO> submitUserFeedback(
            @PathVariable @Pattern(regexp = "TK-\\d{8}") String ticketNo,
            @Valid @RequestBody SupportTicketUserFeedbackDTO request
    ) {
        currentUser.requireRole(ChatConstants.ROLE_USER);
        SupportTicketVO ticket = supportTicketService.submitUserFeedback(
                currentUser.getUserId(), ticketNo, request);
        return Result.success(ticket);
    }

    private boolean isAdministratorWorkspace() {
        WorkspaceRoleResolver resolver = workspaceRoleResolver == null ? new WorkspaceRoleResolver() : workspaceRoleResolver;
        String workspaceRole = resolver.resolve(
                httpServletRequest == null ? null : httpServletRequest.getHeader("X-Workspace-Role"),
                currentUser.getRoleCodes(), ChatConstants.ROLE_USER, ChatConstants.ROLE_AGENT, ChatConstants.ROLE_ADMIN);
        if (!ChatConstants.ROLE_ADMIN.equals(workspaceRole)) {
            return false;
        }
        currentUser.requireRole(ChatConstants.ROLE_ADMIN);
        currentUser.requirePermission("chat:session:audit:view");
        return true;
    }
}
