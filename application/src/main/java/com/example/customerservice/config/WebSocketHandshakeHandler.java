package com.example.customerservice.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 把握手拦截器认证后的用户设置为WebSocket Principal。
 */
@Component
public class WebSocketHandshakeHandler
        extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Object principal =
                attributes.get(
                        WebSocketHandshakeInterceptor
                                .AUTHENTICATED_PRINCIPAL
                );

        if (
                principal
                        instanceof WebSocketUserPrincipal
                        authenticatedPrincipal
        ) {
            return authenticatedPrincipal;
        }

        throw new IllegalStateException(
                "WebSocket握手尚未完成Token认证"
        );
    }
}
