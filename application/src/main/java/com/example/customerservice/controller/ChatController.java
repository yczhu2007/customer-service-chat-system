package com.example.customerservice.controller;

import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.*;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
@Slf4j
@Validated
public class ChatController {

    @Autowired
    private ChatRoutingOperations chatRoutingOperations;

    @Autowired
    private ChatMessageOperations chatMessageOperations;

    @Autowired
    private ChatSessionOperations chatSessionOperations;

    @Autowired
    private ChatPresenceOperations chatPresenceOperations;

    @Autowired
    private IAuthenticationService authenticationService;

    @Autowired
    private Validator validator;

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
        String senderId = principal.getName();
        boolean typing = Boolean.TRUE.equals(request.get("typing"));
        chatPresenceOperations.handleTyping(sessionId, senderId, typing);
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
        response.put("counterpartLastReadMessageId", historyPage.counterpartLastReadMessageId());
        response.put("counterpartLastReadMessageCreateTime", historyPage.counterpartLastReadMessageCreateTime());
        response.put("counterpartLastReadAt", historyPage.counterpartLastReadAt());


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

}
