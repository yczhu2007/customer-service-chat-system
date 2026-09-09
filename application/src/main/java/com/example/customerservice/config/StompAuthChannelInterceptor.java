package com.example.customerservice.config;

import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.TokenService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Set;

/**
 * STOMP 入站帧认证、目的地白名单和订阅权限校验。
 *
 * WebSocket 握手已认证并建立 Principal；这里对每一个 STOMP 帧（包括
 * 非 CONNECT 帧）强制要求该 Principal。Principal 只保存 userId 和角色，
 * 不保存可被日志、调试器或消息头意外暴露的原始访问 Token。
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String APPLICATION_PREFIX = "/app/";
    static final String USER_DESTINATION_PREFIX = "/user/queue/";
    static final Set<String> SEND_DESTINATIONS = Set.of(
            "/app/chat.start", "/app/chat.send", "/app/chat.end", "/app/chat.typing",
            "/app/chat.transfer", "/app/chat.history", "/app/chat.offline.pull",
            "/app/chat.ack", "/app/chat.read", "/app/chat.message.edit",
            "/app/chat.message.recall", "/app/chat.heartbeat"
    );
    static final Set<String> SUBSCRIBE_DESTINATIONS = Set.of(
            "/user/queue/chat", "/user/queue/messages", "/user/queue/errors"
    );
    private static final String CHAT_QUEUE = "/user/queue/chat";

    private final TokenService tokenService;
    private final IAuthenticationService authenticationService;

    public StompAuthChannelInterceptor(
            TokenService tokenService,
            IAuthenticationService authenticationService
    ) {
        this.tokenService = tokenService;
        this.authenticationService = authenticationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        WebSocketUserPrincipal principal = requireCurrentPrincipal(accessor.getUser());
        String userId = requireValidToken(accessor, principal);
        accessor.setUser(principal);

        if (command == StompCommand.SEND) {
            requireAllowedSendDestination(accessor.getDestination());
        } else if (command == StompCommand.SUBSCRIBE) {
            requireAllowedSubscriptionDestination(accessor.getDestination());
            if (CHAT_QUEUE.equals(accessor.getDestination())) {
                requireChatSubscriptionPermission(userId);
            }
        }
        return message;
    }

    private void requireAllowedSendDestination(String destination) {
        requireValidDestination(destination);
        if (!destination.startsWith(APPLICATION_PREFIX)) {
            throw new MessageDeliveryException("客户端只能向 /app 前缀发送 STOMP 消息");
        }
        if (!SEND_DESTINATIONS.contains(destination)) {
            throw new MessageDeliveryException("不允许向该 STOMP 目的地发送消息");
        }
    }

    private void requireAllowedSubscriptionDestination(String destination) {
        requireValidDestination(destination);
        if (!destination.startsWith(USER_DESTINATION_PREFIX)
                || !SUBSCRIBE_DESTINATIONS.contains(destination)) {
            throw new MessageDeliveryException("不允许订阅该 STOMP 目的地");
        }
    }

    private void requireValidDestination(String destination) {
        if (!StringUtils.hasText(destination) || destination.length() > 128
                || destination.indexOf('\u0000') >= 0
                || destination.contains("..") || destination.contains("//")) {
            throw new MessageDeliveryException("STOMP 目的地格式非法");
        }
    }

    private WebSocketUserPrincipal requireCurrentPrincipal(Principal principal) {
        if (!(principal instanceof WebSocketUserPrincipal authenticatedPrincipal)
                || !StringUtils.hasText(authenticatedPrincipal.getName())) {
            throw new MessageDeliveryException("当前 STOMP 连接没有有效用户身份");
        }
        return authenticatedPrincipal;
    }

    private String requireValidToken(StompHeaderAccessor accessor, WebSocketUserPrincipal principal) {
        Object rawToken = accessor.getSessionAttributes() == null ? null
                : accessor.getSessionAttributes().get(WebSocketHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE);
        if (!(rawToken instanceof String token) || !StringUtils.hasText(token)) {
            throw new MessageDeliveryException("当前 STOMP 连接认证已失效");
        }
        String userId = tokenService.resolveUserId(token);
        if (!principal.getName().equals(userId)) {
            throw new MessageDeliveryException("当前 STOMP 连接认证已失效");
        }
        return userId;
    }

    private void requireChatSubscriptionPermission(String userId) {
        try {
            authenticationService.requireChatSubscriptionPermission(userId);
        } catch (IllegalArgumentException exception) {
            throw new MessageDeliveryException(exception.getMessage());
        }
    }
}
