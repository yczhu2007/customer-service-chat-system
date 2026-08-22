package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.dto.AdminDashboardVO;
import com.example.customerservice.dto.AgentDashboardVO;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatManagementQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 客服工作台、管理员仪表盘和会话质检接口。 */
@RestController
@RequestMapping("/chat")
@Validated
public class ChatManagementController {

    private final ChatManagementQueryService managementQueryService;
    private final CurrentUser currentUser;

    public ChatManagementController(
            ChatManagementQueryService managementQueryService,
            CurrentUser currentUser
    ) {
        this.managementQueryService = managementQueryService;
        this.currentUser = currentUser;
    }

    @GetMapping("/agent/dashboard")
    public Result<AgentDashboardVO> findAgentDashboard() {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:agent:dashboard:view");
        return Result.success(
                managementQueryService.findAgentDashboard(currentUser.getUserId())
        );
    }

    @GetMapping("/agent/views")
    public Result<List<AgentSessionViewCountVO>> findAgentSessionViews() {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:view-own");
        return Result.success(
                managementQueryService.findAgentSessionViews(currentUser.getUserId())
        );
    }

    @GetMapping("/agent/views/{viewCode}/sessions")
    public Result<PageResult<ChatSessionListItemVO>> findAgentViewSessions(
            @PathVariable String viewCode,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNo,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0") @Max(value = 100, message = "每页数量不能超过100") long pageSize
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:view-own");

        AgentSessionView view = AgentSessionView.fromCode(viewCode);

        return Result.success(
                managementQueryService.findAgentViewSessions(
                        currentUser.getUserId(),
                        view,
                        pageNo,
                        pageSize
                )
        );
    }

    @GetMapping("/admin/dashboard")
    public Result<AdminDashboardVO> findAdminDashboard() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:admin:dashboard:view");
        return Result.success(managementQueryService.findAdminDashboard());
    }

    @GetMapping("/agent/ratings/summary")
    public Result<RatingSummaryVO> findMyRatingSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:rating:stats:view");
        return Result.success(
                managementQueryService.findRatingSummary(
                        currentUser.getUserId(),
                        from,
                        to
                )
        );
    }

    @GetMapping("/admin/ratings/summary")
    public Result<RatingSummaryVO> findRatingSummary(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:rating:stats:view");
        return Result.success(
                managementQueryService.findRatingSummary(agentId, from, to)
        );
    }

    @GetMapping("/admin/sessions")
    public Result<PageResult<SessionSummaryVO>> searchSessions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String archiveStatus,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNo,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0") @Max(value = 100, message = "每页数量不能超过100") long pageSize
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:session:audit:view");
        return Result.success(
                managementQueryService.searchSessions(
                        userId,
                        agentId,
                        status,
                        archiveStatus,
                        rating,
                        from,
                        to,
                        pageNo,
                        pageSize
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/transfers")
    public Result<List<SessionTransferLogVO>> findTransferLogs(
            @PathVariable
            @NotBlank(message = "会话ID不能为空")
            @Size(max = 64, message = "会话ID长度不能超过64个字符")
            String sessionId
    ) {
        boolean admin = currentUser.hasRole("ADMIN");
        if (!admin) {
            currentUser.requireRole("AGENT");
        }
        currentUser.requirePermission("chat:session:transfer-log:view");
        return Result.success(
                managementQueryService.findTransferLogs(
                        currentUser.getUserId(),
                        admin,
                        sessionId
                )
        );
    }
}
