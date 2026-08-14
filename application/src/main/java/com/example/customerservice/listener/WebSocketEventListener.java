package com.example.customerservice.listener;

import com.example.customerservice.service.ChatPresenceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;


@Component
@Slf4j
public class WebSocketEventListener {

    private final ChatPresenceOperations chatPresenceOperations;


    public WebSocketEventListener(
            ChatPresenceOperations chatPresenceOperations
    ) {

        this.chatPresenceOperations =
                chatPresenceOperations;
    }


    /**
     * STOMP连接成功
     */
    @EventListener
    public void connect(
            SessionConnectEvent event
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(
                        event.getMessage()
                );


        String sessionId =
                accessor.getSessionId();


        Principal principal =
                accessor.getUser();


        /*
         * 1. 检查WebSocket会话ID
         */
        if (
                sessionId == null ||
                        sessionId.isBlank()
        ) {

            log.info(
                    "STOMP连接失败：没有sessionId"
            );

            return;
        }


        /*
         * 2. 检查当前用户身份
         */
        if (principal == null) {

            log.info(
                    "STOMP连接失败：没有Principal"
            );

            return;
        }


        String userId =
                principal.getName();


        /*
         * 统一登记在线状态、双向连接映射、
         * 300秒TTL和初始心跳超时时间。
         */
        chatPresenceOperations.registerOnline(
                userId,
                sessionId
        );


        log.info(
                "STOMP连接成功，用户Principal："
                        + userId
        );


        log.info(
                "WebSocket SessionId："
                        + sessionId
        );


        log.info(
                "WebSocket在线状态已写入Redis"
        );
        log.info(
                "初始心跳登记完成，用户："
                        + userId
        );
    }


    /**
     * STOMP/WebSocket断开
     */
    @EventListener
    public void disconnect(
            SessionDisconnectEvent event
    ) {

        String sessionId =
                event.getSessionId();


        if (
                sessionId == null ||
                        sessionId.isBlank()
        ) {

            log.info(
                    "WebSocket断开事件没有sessionId"
            );

            return;
        }


        /*
         * 在线状态清理、旧连接保护和会话结算
         * 统一由ChatService处理。
         */
        try {

            chatPresenceOperations.handleDisconnect(
                    sessionId
            );

        } catch (Exception e) {

            log.info(
                    "WebSocket业务断线处理失败，sessionId："
                            + sessionId
                            + "，原因："
                            + e.getMessage()
            );
        }


        log.info(
                "WebSocket断开，sessionId："
                        + sessionId
        );
    }
}
