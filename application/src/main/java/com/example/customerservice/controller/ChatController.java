package com.example.customerservice.controller;

import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.dto.*;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.security.AuthRateLimiter;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.service.ChatSessionQueryService;
import com.example.customerservice.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/chat")
@Slf4j
@Validated
public class ChatController {

    @Autowired
    private ChatRoutingOperations chatRoutingOperations;

    @Autowired
    private ChatAgentOperations chatAgentOperations;

    @Autowired
    private ChatMessageOperations chatMessageOperations;

    @Autowired
    private ChatSessionOperations chatSessionOperations;

    @Autowired
    private ChatPresenceOperations chatPresenceOperations;

    @Autowired
    private IAuthenticationService authenticationService;

    @Autowired
    private MessagePersistService messagePersistService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private AuthRateLimiter authRateLimiter;

    @Autowired
    private ChatSessionQueryService chatSessionQueryService;

    @Autowired
    private Validator validator;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private ChatRedisRepository chatRedisRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid
            @RequestBody
            LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkLogin(servletRequest);
        return Result.success(
                authenticationService.login(request)
        );
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader("Authorization") String authorization
    ) {
        authenticationService.logout(
                authorization,
                currentUser.getUserId()
        );
        return Result.successMessage("退出登录成功");
    }

    @PostMapping("/ws-ticket")
    public Result<WebSocketTicketResponse> issueWebSocketTicket(
            @RequestHeader("Authorization") String authorization
    ) {
        return Result.success(
                authenticationService.issueWebSocketTicket(
                        authorization,
                        currentUser.getUserId()
                )
        );
    }
    /**
     * 普通用户显式发起或恢复咨询。
     * 客户端发送地址：/app/chat.start
     */
    @PostMapping("/queue/cancel")
    public Result<Void> cancelQueue() {
        currentUser.requireRole("USER");
        currentUser.requirePermission("chat:user:access");
        String userId = currentUser.getUserId();
        Long removed = chatRedisRepository.cancelQueueEntry(userId);
        boolean cancelled = removed != null && removed > 0;
        try {
            chatRoutingOperations.refreshWaitingPositions();
        } catch (RuntimeException exception) {
            log.warn("刷新排队位置通知失败，取消操作已完成，userId={}", userId, exception);
        }
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "QUEUE_CANCELLED");
            messagingTemplate.convertAndSendToUser(userId, "/queue/chat", event);
        } catch (RuntimeException exception) {
            log.warn("发送取消排队通知失败，取消操作已完成，userId={}", userId, exception);
        }
        return Result.successMessage(cancelled ? "已取消排队" : "当前未在等待队列中");
    }

    @MessageMapping("/chat.start")
    public void startConsultation(
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalArgumentException(
                    "当前STOMP连接没有用户身份"
            );
        }

        String userId = principal.getName();
        Set<String> roleCodes =
                authenticationService.findRoleCodesByUserId(
                        userId
                );

        if (
                roleCodes == null ||
                        !roleCodes.contains("USER")
        ) {
            throw new IllegalArgumentException(
                    "只有普通用户可以发起咨询"
            );
        }

        requireWebSocketPermission(
                userId,
                "chat:user:access"
        );
        chatRoutingOperations.onUserConnected(
                userId
        );

        log.info(
                "用户发起咨询，userId：{}",
                userId
        );
    }


    /**
     * 当前登录客服上线。
     *
     * agentId从Token认证主体中获取，
     * 不接受客户端任意传入。
     */
    @PostMapping("/agent/online")
    public Result<Void> agentOnline() {

        /*
         * 必须同时拥有AGENT角色
         * 和客服上线权限。
         */
        currentUser.requireRole(
                "AGENT"
        );


        currentUser.requirePermission(
                "chat:agent:online"
        );


        String agentId =
                currentUser.getUserId();


        chatAgentOperations.agentOnline(
                agentId
        );


        return Result.successMessage(
                "上线成功"
        );
    }



    /**
     * 当前登录客服下线。
     *
     * agentId从Token认证主体中获取。
     */
    @PostMapping("/agent/offline")
    public Result<Void> agentOffline() {

        currentUser.requireRole(
                "AGENT"
        );


        currentUser.requirePermission(
                "chat:agent:offline"
        );


        String agentId =
                currentUser.getUserId();


        chatAgentOperations.agentOffline(
                agentId
        );


        return Result.successMessage(
                "下线成功"
        );
    }

    /** 管理员将指定客服加入VIP坐席技能组。 */
    @PutMapping("/agents/{agentLoginNumber}/vip-skill")
    public Result<Void> enableAgentVipSkill(
            @PathVariable
            @NotBlank(message = "客服登录编号不能为空")
            @Size(max = 64, message = "客服登录编号长度不能超过64个字符")
            String agentLoginNumber
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        chatAgentOperations.setAgentVipSkill(requireEnabledAgentId(agentLoginNumber), true);
        return Result.successMessage("已加入VIP坐席技能组");
    }

    /** 管理员将指定客服移出VIP坐席技能组。 */
    @DeleteMapping("/agents/{agentLoginNumber}/vip-skill")
    public Result<Void> disableAgentVipSkill(
            @PathVariable
            @NotBlank(message = "客服登录编号不能为空")
            @Size(max = 64, message = "客服登录编号长度不能超过64个字符")
            String agentLoginNumber
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        chatAgentOperations.setAgentVipSkill(requireEnabledAgentId(agentLoginNumber), false);
        return Result.successMessage("已移出VIP坐席技能组");
    }

    /** 管理员查询当前VIP坐席技能组。 */
    @GetMapping("/agents/vip-skill")
    public Result<Set<String>> findVipSkillAgents() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        return Result.success(
                chatAgentOperations.findVipSkillAgentIds().stream()
                        .map(sysUserMapper::selectById)
                        .filter(user -> user != null && user.getUsername() != null && !user.getUsername().isBlank())
                        .map(SysUser::getUsername)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    private String requireEnabledAgentId(String loginNumber) {
        SysUser agent = sysUserMapper.findByUsername(loginNumber.trim());
        if (agent == null || !"ENABLED".equals(agent.getStatus())) {
            throw new IllegalArgumentException("客服登录编号不存在或账号已禁用");
        }
        Set<String> roles = sysUserRoleMapper.findRoleCodesByUserId(agent.getId());
        if (roles == null || !roles.contains("AGENT")) {
            throw new IllegalArgumentException("该登录编号不是客服账号");
        }
        return agent.getId();
    }

    /** 管理员分页查看消息落库死信。 */
    @GetMapping("/admin/deadletters")
    public Result<PageResult<DeadLetterMessageVO>> findDeadLetters(
            @RequestParam(defaultValue = "1")
            long pageNo,
            @RequestParam(defaultValue = "20")
            long pageSize
    ) {
        requireDeadLetterManagementPermission();
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNo必须大于0，pageSize必须在1到100之间");
        }
        return Result.success(
                messagePersistService.findDeadLetters(pageNo, pageSize)
        );
    }

    /** 管理员手工重放指定死信消息。 */
    @PostMapping("/admin/deadletters/{messageId}/replay")
    public Result<Void> replayDeadLetter(
            @PathVariable
            @NotBlank(message = "消息ID不能为空")
            @Size(max = 64, message = "消息ID长度不能超过64个字符")
            String messageId
    ) {
        requireDeadLetterManagementPermission();
        messagePersistService.replayDeadLetter(messageId);
        return Result.successMessage("死信消息已提交重放");
    }

    @DeleteMapping("/admin/deadletters/{messageId}")
    public Result<Void> deleteDeadLetter(
            @PathVariable
            @NotBlank(message = "消息ID不能为空")
            @Size(max = 64, message = "消息ID长度不能超过64个字符")
            String messageId
    ) {
        requireDeadLetterManagementPermission();
        messagePersistService.deleteDeadLetter(messageId);
        return Result.successMessage("死信消息已删除");
    }

    private void requireDeadLetterManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:message:deadletter:manage");
    }

    /** 当前用户查看自己的会话历史列表（用户或客服视角自动判断），支持归档状态筛选。 */
    @GetMapping("/sessions")
    public Result<PageResult<ChatSessionListItemVO>> findMySessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String archiveStatus,
            @RequestParam(defaultValue = "1")
            @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于0")
            long pageNo,
            @RequestParam(defaultValue = "20")
            @jakarta.validation.constraints.Min(value = 1, message = "每页数量必须大于0")
            @jakarta.validation.constraints.Max(value = 100, message = "每页数量不能超过100")
            long pageSize
    ) {
        currentUser.requirePermission("chat:session:view-own");
        return Result.success(
                chatSessionQueryService.findMySessions(
                        currentUser.getUserId(),
                        resolveSessionParticipantType(),
                        status,
                        archiveStatus,
                        pageNo,
                        pageSize
                )
        );
    }

    /** 查询指定会话的满意度评价。 */
    @GetMapping("/sessions/{sessionId}/rating")
    public Result<SessionRatingVO> getSessionRating(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        currentUser.requireRole("USER");
        return Result.success(
                chatSessionQueryService.getSessionRating(
                        currentUser.getUserId(), sessionId));
    }

    /** 用户对已结束的会话提交满意度评价，每会话仅一次。 */
    @PostMapping("/sessions/{sessionId}/rating")
    public Result<SessionRatingVO> rateSession(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SessionRatingDTO request
    ) {
        currentUser.requireRole("USER");
        currentUser.requirePermission("chat:session:rate");
        return Result.success(
                chatSessionQueryService.rateSession(
                        currentUser.getUserId(), sessionId, request));
    }

    /** 客服查看当前会话中用户的基本信息侧栏。 */
    @GetMapping("/sessions/{sessionId}/user-profile")
    public Result<UserProfileSidebarVO> getUserProfileSidebar(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:user-profile:view");
        return Result.success(
                chatSessionQueryService.getUserProfileSidebar(
                        currentUser.getUserId(), sessionId));
    }

    /** 查看会话元数据。 */
    @GetMapping("/sessions/{sessionId}/metadata")
    public Result<ChatSessionMetadataVO> getSessionMetadata(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        Set<String> roleCodes = currentUser.getRoleCodes();
        String actorId = currentUser.getUserId();
        if (roleCodes != null && roleCodes.contains("ADMIN")) {
            currentUser.requireRole("ADMIN");
            currentUser.requirePermission("chat:session:audit:view");
            return Result.success(
                    chatSessionQueryService.getSessionMetadata(actorId, true, sessionId)
            );
        }
        if (roleCodes != null
                && (roleCodes.contains("USER") || roleCodes.contains("AGENT"))) {
            currentUser.requirePermission("chat:session:view-own");
            return Result.success(
                    chatSessionQueryService.getSessionMetadata(actorId, false, sessionId)
            );
        }
        throw new IllegalArgumentException("只有普通用户、客服或管理员可以查看会话元数据");
    }

    /** 客服更新自己参与会话的元数据。 */
    @PutMapping("/sessions/{sessionId}/metadata")
    public Result<ChatSessionMetadataVO> updateSessionMetadata(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody ChatSessionMetadataUpdateDTO request
    ) {
        Set<String> roleCodes = currentUser.getRoleCodes();
        if (roleCodes != null && roleCodes.contains("ADMIN")) {
            throw new IllegalArgumentException("管理员不能更新会话元数据");
        }
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:metadata:update");
        return Result.success(
                chatSessionQueryService.updateSessionMetadata(
                        currentUser.getUserId(),
                        sessionId,
                        request
                )
        );
    }

    /** 查询当前排队状态（在线客服数、队列大小、我的位置、预估等待时间）。 */
    @GetMapping("/queue-status")
    public Result<QueueStatusVO> getQueueStatus() {
        return Result.success(
                chatSessionQueryService.getQueueStatus(currentUser.getUserId()));
    }

    /** 客服对已结束会话设置/更新归档状态（COMPLETED/PENDING/ON_HOLD/OTHER），允许 reopen。 */
    @PutMapping("/sessions/{sessionId}/archive-status")
    public Result<Void> setArchiveStatus(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SessionArchiveDTO request
    ) {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:session:archive");
        chatSessionQueryService.setArchiveStatus(
                currentUser.getUserId(), sessionId, request);
        return Result.successMessage("归档状态更新成功");
    }

    /** 客服独立保存已结束会话的单条归档备注。 */
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

    /** 管理员查看已结束会话的归档统计概览。 */
    @GetMapping("/admin/archive-stats")
    public Result<ArchiveStatsVO> findArchiveStats() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:archive:stats");
        return Result.success(chatSessionQueryService.findArchiveStats());
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(
            @org.springframework.messaging.handler.annotation.Payload Map<String, Object> request,
            Principal principal
    ) {
        if (principal == null) throw new IllegalArgumentException("当前STOMP连接没有用户身份");
        String sessionId = request == null ? null : String.valueOf(request.get("sessionId"));
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) throw new IllegalArgumentException("会话不存在");
        String senderId = principal.getName();
        String recipientId;
        if (senderId.equals(session.getUserId())) recipientId = session.getAgentId();
        else if (senderId.equals(session.getAgentId())) recipientId = session.getUserId();
        else throw new IllegalArgumentException("无权操作该会话");
        if (recipientId == null || recipientId.isBlank()) return;
        boolean typing = Boolean.TRUE.equals(request.get("typing"));
        String key = RedisConstants.SESSION_TYPING + sessionId + ":" + senderId;
        if (typing) chatRedisRepository.setValue(key, "1", 5, TimeUnit.SECONDS);
        else chatRedisRepository.delete(key);
        Map<String, Object> event = new HashMap<>();
        event.put("event", "TYPING");
        event.put("sessionId", sessionId);
        event.put("senderId", senderId);
        event.put("typing", typing);
        messagingTemplate.convertAndSendToUser(recipientId, "/queue/chat", event);
    }

    @MessageMapping("/chat.send")
    public void handleSend(
            @Valid
            ChatMessageDTO request,
            Principal principal
    ) {

        if (principal == null) {
            throw new IllegalArgumentException(
                    "当前STOMP连接没有用户身份"
            );
        }

        requireValidStompPayload(request, "聊天消息不能为空");

        /* STOMP @Valid 不保证触发，保留业务字段的额外白名单校验。 */
        if (
                request.getSessionId() == null ||
                        request.getSessionId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }
        if (request.getSessionId().length() > 64) {
            throw new IllegalArgumentException(
                    "sessionId长度不能超过64个字符"
            );
        }
        if (
                request.getClientMsgId() == null ||
                        request.getClientMsgId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "clientMsgId不能为空"
            );
        }
        if (request.getClientMsgId().length() > 64) {
            throw new IllegalArgumentException(
                    "clientMsgId长度不能超过64个字符"
            );
        }
        if (
                request.getType() == null ||
                        request.getType().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "消息类型不能为空"
            );
        }
        if (!request.getType().toUpperCase()
                .matches("TEXT|IMAGE|FILE")) {
            throw new IllegalArgumentException(
                    "消息类型只支持 TEXT、IMAGE、FILE"
            );
        }
        if (
                request.getContent() == null ||
                        request.getContent().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "消息内容不能为空"
            );
        }
        if (request.getContent().length() > 4000) {
            throw new IllegalArgumentException(
                    "消息内容不能超过4000个字符"
            );
        }

        ChatMessage message =
                request.toEntity(
                        principal.getName()
                );
        int result =
                chatMessageOperations.handleMessage(message);


        log.info(
                "handleSend处理结果："
                        + result
        );
    }
    /**
     * 结束聊天会话
     *
     * 客户端发送地址：
     * /app/chat.end
     */
    @MessageMapping("/chat.end")
    public void handleEndSession(
            @Valid EndSessionRequest request,
            Principal principal
    ) {

        if (principal == null) {
            throw new IllegalArgumentException(
                    "当前STOMP连接没有用户身份"
            );
        }


        requireValidStompPayload(request, "结束会话请求不能为空");
        if (
                request.getSessionId() == null ||
                        request.getSessionId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }


        String agentId =
                principal.getName();


        requireWebSocketRole(
                agentId,
                "AGENT"
        );


        requireWebSocketPermission(
                agentId,
                "chat:session:end"
        );


        chatSessionOperations.endSessionByAgent(
                request.getSessionId(),
                agentId
        );
        log.info(
                "结束会话请求处理完成，sessionId："
                        + request.getSessionId()
                        + "，操作者："
                        + agentId
        );
    }

    /**
     * 客服转接自己正在处理的会话。
     * 客户端发送地址：/app/chat.transfer
     */
    @MessageMapping("/chat.transfer")
    public void handleTransferSession(
            @Valid TransferSessionRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalArgumentException("当前STOMP连接没有用户身份");
        }
        requireValidStompPayload(request, "转接会话请求不能为空");
        String sourceAgentId = principal.getName();
        requireWebSocketRole(sourceAgentId, "AGENT");
        requireWebSocketPermission(sourceAgentId, "chat:session:transfer");
        chatSessionOperations.transferSession(
                request.getSessionId(),
                sourceAgentId,
                request.getTargetAgentId()
        );
    }
    /**
     * 查询聊天历史
     *
     * 客户端发送地址：
     * /app/chat.history
     *
     * 返回地址：
     * /user/queue/chat
     */
    @MessageMapping("/chat.history")
    @SendToUser("/queue/chat")
    public Map<String, Object> getHistory(
            @Valid HistoryRequest request,
            Principal principal
    ) {

        /*
         * 1. 检查STOMP身份
         */
        if (principal == null) {
            throw new IllegalArgumentException(
                    "当前STOMP连接没有用户身份"
            );
        }


        /*
         * 2. 检查请求参数
         */
        requireValidStompPayload(request, "历史消息请求不能为空");
        if (
                request.getSessionId() == null ||
                        request.getSessionId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }


        /*
         * 3. 查询历史消息
         */
        ChatHistoryPage historyPage = chatMessageOperations.getHistory(
                        request.getSessionId(),
                        principal.getName(),
                        request.getBeforeMessageId(),
                        request.getPageSize()
        );

        List<ChatMessageDTO> messages = historyPage.records()
                        .stream()
                        .map(
                                ChatMessageDTO::fromEntity
                        )
                        .toList();


        /*
         * 4. 构造返回结果
         */
        Map<String, Object> response =
                new HashMap<>();


        response.put(
                "event",
                "CHAT_HISTORY"
        );


        response.put(
                "sessionId",
                request.getSessionId()
        );

        response.put("requestId", request.getRequestId());


        response.put(
                "count",
                messages.size()
        );

        response.put("pageSize", historyPage.pageSize());
        response.put("total", historyPage.total());
        response.put("nextCursor", historyPage.nextCursor());
        response.put("hasMore", historyPage.hasMore());
        response.put("unreadCount", historyPage.unreadCount());


        response.put(
                "messages",
                messages
        );


        log.info(
                "历史记录已返回给："
                        + principal.getName()
        );


        return response;
    }
    /**
     * 当前用户主动拉取离线消息
     */
    @MessageMapping("/chat.offline.pull")
    public void pullOfflineMessages(
            Principal principal
    ) {

        if (principal == null) {

            throw new IllegalArgumentException(
                    "当前用户身份不存在"
            );
        }


        String userId =
                principal.getName();


        chatMessageOperations.pullOfflineMessages(
                userId
        );
    }
    /**
     * 客户端确认已经收到聊天消息
     */
    @MessageMapping("/chat.ack")
    public void handleAck(
            @Valid AckRequest request,
            Principal principal
    ) {

        if (principal == null) {

            throw new IllegalArgumentException(
                    "当前用户身份不存在"
            );
        }


        requireValidStompPayload(request, "ACK请求不能为空");
        chatMessageOperations.handleAck(
                request.getMessageId(),
                principal.getName()
        );
    }

    /**
     * 将指定消息及其之前由对方发送的消息批量标记为已读。
     * 客户端发送地址：/app/chat.read
     * 当前用户和会话对方均从/user/queue/messages接收MESSAGES_READ事件。
     */
    @MessageMapping("/chat.read")
    @SendToUser("/queue/messages")
    public MessageReadResult markMessagesRead(
            @Valid ReadMessagesRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalArgumentException("当前用户身份不存在");
        }
        requireValidStompPayload(request, "已读消息请求不能为空");
        return chatMessageOperations.markMessagesRead(
                request.getSessionId(),
                request.getLastReadMessageId(),
                principal.getName()
        );
    }

    /**
     * 撤回当前用户自己发送且仍在允许时间内的消息。
     * 会话双方均从 /user/queue/messages 接收 MESSAGE_RECALLED 事件。
     */
    @MessageMapping("/chat.message.recall")
    @SendToUser("/queue/messages")
    public MessageMutationResult recallMessage(
            @Valid RecallMessageRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalArgumentException("当前用户身份不存在");
        }
        requireValidStompPayload(request, "撤回消息请求不能为空");
        return chatMessageOperations.recallMessage(
                request.getMessageId(),
                principal.getName()
        );
    }
    /**
     * 接收客户端心跳
     *
     * 浏览器发送地址：
     * /app/chat.heartbeat
     */
    @MessageMapping("/chat.heartbeat")
    public void handleHeartbeat(
            Principal principal,
            @Header("simpSessionId")
            String wsSessionId
    ) {

        /*
         * Principal中的name就是握手时传入的userId
         */
        if (principal == null) {

            throw new IllegalArgumentException(
                    "当前WebSocket连接没有用户身份"
            );
        }


        String userId =
                principal.getName();


        chatPresenceOperations.handleHeartbeat(
                userId,
                wsSessionId
        );
    }


    /**
     * STOMP 方法参数不会在所有消息转换路径上自动触发 Bean Validation，
     * 因此在进入业务服务前显式执行一次，避免 null、空白和超限字段绕过校验。
     */
    private void requireValidStompPayload(Object payload, String nullMessage) {
        if (payload == null) {
            throw new IllegalArgumentException(nullMessage);
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(payload);
        if (violations != null && !violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
    }

    /**
     * 校验WebSocket用户是否具有指定角色。
     *
     * STOMP消息不经过Shiro的HTTP过滤器，
     * 因此必须根据认证后的Principal再次查询RBAC数据。
     */
    private void requireWebSocketRole(
            String userId,
            String roleCode
    ) {

        authenticationService.requireRole(
                userId,
                roleCode
        );
    }


    /**
     * 校验WebSocket用户是否具有指定权限。
     */
    private void requireWebSocketPermission(
            String userId,
            String permissionCode
    ) {

        authenticationService.requirePermission(
                userId,
                permissionCode
        );
    }

    private SessionParticipantType resolveSessionParticipantType() {
        Set<String> roleCodes = currentUser.getRoleCodes();
        if (roleCodes.contains("AGENT")) {
            return SessionParticipantType.AGENT;
        }
        if (roleCodes.contains("USER")) {
            return SessionParticipantType.USER;
        }
        throw new IllegalArgumentException("只有普通用户或客服可以查看自己的会话列表");
    }
}
