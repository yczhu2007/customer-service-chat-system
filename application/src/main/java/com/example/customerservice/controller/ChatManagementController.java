package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.dto.AdminDashboardVO;
import com.example.customerservice.dto.AdminSessionCreateDTO;
import com.example.customerservice.dto.AgentDashboardVO;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatSessionMetadataVO;
import com.example.customerservice.dto.ChatMessageSearchVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.ChatSessionMetadataUpdateDTO;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.ChatManagementQueryService;
import com.example.customerservice.service.ChatSessionOperations;
import com.example.customerservice.service.ChatSessionQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 客服工作台、管理员仪表盘和会话质检接口。 */
@RestController
@RequestMapping("/chat")
@Validated
public class ChatManagementController {

    private final ChatManagementQueryService managementQueryService;
    private final CurrentUser currentUser;

    @Autowired
    private ChatSessionQueryService sessionQueryService;

    @Autowired
    private ChatSessionOperations sessionOperations;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    public ChatManagementController(
            ChatManagementQueryService managementQueryService,
            CurrentUser currentUser
    ) {
        this.managementQueryService = managementQueryService;
        this.currentUser = currentUser;
    }

    @PostMapping("/admin/sessions")
    public Result<SessionSummaryVO> createAdminSession(
            @Valid @RequestBody AdminSessionCreateDTO request
    ) {
        requireSessionManagementPermission();
        SysUser user = requireUserByLoginNumber(request.getUserLoginNumber());
        SysUser agent = requireUserByLoginNumber(request.getAgentLoginNumber());
        Set<String> userRoles = userRoleMapper.findRoleCodesByUserId(user.getId());
        Set<String> agentRoles = userRoleMapper.findRoleCodesByUserId(agent.getId());
        if (userRoles == null || !userRoles.contains("USER")) {
            throw new IllegalArgumentException("用户登录编号对应的账号没有USER角色");
        }
        if (agentRoles == null || !agentRoles.contains("AGENT")) {
            throw new IllegalArgumentException("客服登录编号对应的账号没有AGENT角色");
        }
        if (sessionMapper.findActiveByUserId(user.getId()) != null) {
            throw new IllegalArgumentException("该用户已有进行中的会话");
        }
        ChatSession session = sessionOperations.createSession(user.getId(), agent.getId());
        sessionOperations.notifyBothParties(session);
        return Result.success(new SessionSummaryVO(
                session.getId(), user.getId(), user.getUsername(), agent.getId(), agent.getUsername(),
                session.getStatus(), session.getTitle(), session.getCreateTime(), session.getEndTime(), null, null, 0L, null, null, null
        ));
    }

    @DeleteMapping("/admin/messages/{messageId}")
    public Result<Void> deleteAdminMessage(@PathVariable String messageId) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:session:audit:view");
        if (messageId == null || messageId.isBlank() || messageId.length() > 64) {
            throw new IllegalArgumentException("消息ID无效");
        }
        if (messageMapper.deleteById(messageId) == 0) {
            throw new IllegalArgumentException("消息不存在");
        }
        return Result.successMessage("消息已删除");
    }

    @PutMapping("/admin/sessions/{sessionId}")
    public Result<ChatSessionMetadataVO> updateAdminSession(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody ChatSessionMetadataUpdateDTO request
    ) {
        requireSessionManagementPermission();
        return Result.success(sessionQueryService.updateSessionMetadataAsAdmin(sessionId, request));
    }

    @DeleteMapping("/admin/sessions/{sessionId}")
    public Result<Void> deleteAdminSession(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        requireSessionManagementPermission();
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if ("ACTIVE".equals(session.getStatus()) && session.getAgentId() != null) {
            sessionOperations.endSessionByAgent(sessionId, session.getAgentId());
        }
        return Result.successMessage("会话已结束");
    }

    private void requireSessionManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:session:audit:view");
    }

    private SysUser requireUserByLoginNumber(String loginNumber) {
        SysUser user = userMapper.findByUsername(loginNumber == null ? null : loginNumber.trim());
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            throw new IllegalArgumentException("登录编号不存在或账号已禁用");
        }
        return user;
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
            @RequestParam(required = false) String ticketStatus,
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
                        ticketStatus,
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
            @RequestParam(required = false) String userLoginNumber,
            @RequestParam(required = false) String agentLoginNumber,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String archiveStatus,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String ticketNo,
            @RequestParam(required = false) String ticketStatus,
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
                        userLoginNumber,
                        agentLoginNumber,
                        status,
                        archiveStatus,
                        rating,
                        ticketNo,
                        ticketStatus,
                        from,
                        to,
                        pageNo,
                        pageSize
                )
        );
    }

    @GetMapping("/admin/messages/search")
    public Result<PageResult<ChatMessageSearchVO>> searchAdminMessages(@RequestParam @NotBlank @Size(max = 100) String keyword, @RequestParam(required = false) String agentId, @RequestParam(defaultValue = "1") @Min(1) long pageNo, @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:session:audit:view");
        return Result.success(managementQueryService.searchMessages(keyword, agentId, pageNo, pageSize));
    }

    @GetMapping("/agent/messages/search")
    public Result<PageResult<ChatMessageSearchVO>> searchAgentMessages(@RequestParam @NotBlank @Size(max = 100) String keyword, @RequestParam(defaultValue = "1") @Min(1) long pageNo, @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:view-own");
        return Result.success(managementQueryService.searchMessages(keyword, currentUser.getUserId(), pageNo, pageSize));
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
