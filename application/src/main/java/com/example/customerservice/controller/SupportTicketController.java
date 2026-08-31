package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketUserFeedbackDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.dto.SupportTicketStatusHistoryVO;
import com.example.customerservice.security.CurrentUser;
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

    public SupportTicketController(
            SupportTicketService supportTicketService,
            CurrentUser currentUser,
            HttpServletRequest httpServletRequest
    ) {
        this.supportTicketService = supportTicketService;
        this.currentUser = currentUser;
        this.httpServletRequest = httpServletRequest;
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
        currentUser.requireRole("AGENT");
        return Result.success(supportTicketService.createTicket(
                currentUser.getUserId(), sessionId, request
        ));
    }

    @PatchMapping("/tickets/{ticketNo}")
    public Result<SupportTicketVO> updateTicket(
            @PathVariable @Pattern(regexp = "TK-\\d{8}") String ticketNo,
            @Valid @RequestBody SupportTicketUpdateDTO request
    ) {
        currentUser.requireRole("AGENT");
        return Result.success(supportTicketService.updateTicket(
                currentUser.getUserId(), ticketNo, request
        ));
    }

    @PostMapping("/tickets/{ticketNo}/user-feedback")
    public Result<SupportTicketVO> submitUserFeedback(
            @PathVariable @Pattern(regexp = "TK-\\d{8}") String ticketNo,
            @Valid @RequestBody SupportTicketUserFeedbackDTO request
    ) {
        currentUser.requireRole("USER");
        SupportTicketVO ticket = "CONFIRM".equals(request.getAction())
                ? supportTicketService.confirmResolution(currentUser.getUserId(), ticketNo, request.getVersion())
                : supportTicketService.requestFurtherHandling(currentUser.getUserId(), ticketNo, request.getVersion());
        return Result.success(ticket);
    }

    private boolean isAdministratorWorkspace() {
        String workspaceRole = httpServletRequest == null ? null : httpServletRequest.getHeader("X-Workspace-Role");
        if (workspaceRole == null || workspaceRole.isBlank()) {
            return currentUser.getRoleCodes().contains("ADMIN");
        }
        if (!"USER".equals(workspaceRole) && !"AGENT".equals(workspaceRole) && !"ADMIN".equals(workspaceRole)) {
            throw new IllegalArgumentException("当前工作台角色无效");
        }
        if (!currentUser.getRoleCodes().contains(workspaceRole)) {
            throw new IllegalArgumentException("当前工作台角色无效");
        }
        if (!"ADMIN".equals(workspaceRole)) {
            return false;
        }
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:session:audit:view");
        return true;
    }
}
