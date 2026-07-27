package com.example.customerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP/WebSocket配置。
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor
            stompAuthChannelInterceptor;

    private final WebSocketHandshakeInterceptor
            webSocketHandshakeInterceptor;

    private final WebSocketHandshakeHandler
            webSocketHandshakeHandler;

    private final String[] allowedOriginPatterns;

    public StompWebSocketConfig(
            StompAuthChannelInterceptor
                    stompAuthChannelInterceptor,
            WebSocketHandshakeInterceptor
                    webSocketHandshakeInterceptor,
            WebSocketHandshakeHandler
                    webSocketHandshakeHandler,
            @Value("${app.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            String allowedOriginPatterns
    ) {
        this.stompAuthChannelInterceptor =
                stompAuthChannelInterceptor;
        this.webSocketHandshakeInterceptor =
                webSocketHandshakeInterceptor;
        this.webSocketHandshakeHandler =
                webSocketHandshakeHandler;

        this.allowedOriginPatterns =
                StringUtils
                        .commaDelimitedListToStringArray(
                                allowedOriginPatterns
                        );
    }

    /**
     * 注册WebSocket握手地址。
     *
     * Token在HTTP握手阶段完成认证，
     * 认证后的用户被设置为WebSocket Principal。
     */
    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry.addEndpoint(
                        "/ws/chat"
                )
                .setAllowedOriginPatterns(
                        allowedOriginPatterns
                )
                .addInterceptors(
                        webSocketHandshakeInterceptor
                )
                .setHandshakeHandler(
                        webSocketHandshakeHandler
                );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                stompAuthChannelInterceptor
        );
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.enableSimpleBroker(
                "/queue",
                "/topic"
        );

        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }
}
