package com.example.customerservice.controller;

import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.*;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


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


    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid
            @RequestBody
            LoginRequest request
    ) {
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
                        !roleCodes.contains("USER") ||
                        roleCodes.contains("AGENT") ||
                        roleCodes.contains("ADMIN")
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
    @PutMapping("/agents/{agentId}/vip-skill")
    public Result<Void> enableAgentVipSkill(
            @PathVariable
            @NotBlank(message = "客服ID不能为空")
            @Size(max = 64, message = "客服ID长度不能超过64个字符")
            String agentId
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        chatAgentOperations.setAgentVipSkill(agentId, true);
        return Result.successMessage("已加入VIP坐席技能组");
    }

    /** 管理员将指定客服移出VIP坐席技能组。 */
    @DeleteMapping("/agents/{agentId}/vip-skill")
    public Result<Void> disableAgentVipSkill(
            @PathVariable
            @NotBlank(message = "客服ID不能为空")
            @Size(max = 64, message = "客服ID长度不能超过64个字符")
            String agentId
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        chatAgentOperations.setAgentVipSkill(agentId, false);
        return Result.successMessage("已移出VIP坐席技能组");
    }

    /** 管理员查询当前VIP坐席技能组。 */
    @GetMapping("/agents/vip-skill")
    public Result<Set<String>> findVipSkillAgents() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        return Result.success(chatAgentOperations.findVipSkillAgentIds());
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

    private void requireDeadLetterManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:message:deadletter:manage");
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

        if (request == null) {
            throw new IllegalArgumentException(
                    "聊天消息不能为空"
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


        if (
                request == null ||
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
        if (
                request == null ||
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


        if (request == null) {

            throw new IllegalArgumentException(
                    "ACK请求不能为空"
            );
        }
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
        return chatMessageOperations.markMessagesRead(
                request.getSessionId(),
                request.getLastReadMessageId(),
                principal.getName()
        );
    }

    /**
     * 编辑当前用户自己发送且仍在允许时间内的文本消息。
     * 会话双方均从 /user/queue/messages 接收 MESSAGE_EDITED 事件。
     */
    @MessageMapping("/chat.message.edit")
    @SendToUser("/queue/messages")
    public MessageMutationResult editMessage(
            @Valid EditMessageRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalArgumentException("当前用户身份不存在");
        }
        return chatMessageOperations.editMessage(
                request.getMessageId(),
                request.getContent(),
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
}
