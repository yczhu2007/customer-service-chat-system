package com.example.customerservice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    private final TaskScheduler stompHeartbeatTaskScheduler;

    private final ThreadPoolTaskExecutor stompOutboundExecutor;

    private final Environment environment;

    public StompWebSocketConfig(
            StompAuthChannelInterceptor
                    stompAuthChannelInterceptor,
            WebSocketHandshakeInterceptor
                    webSocketHandshakeInterceptor,
            WebSocketHandshakeHandler
                    webSocketHandshakeHandler,
            @Qualifier("stompHeartbeatTaskScheduler")
            TaskScheduler stompHeartbeatTaskScheduler,
            @Qualifier("stompOutboundExecutor")
            ThreadPoolTaskExecutor stompOutboundExecutor,
            Environment environment,
            @Value("${app.websocket.allowed-origin-patterns}")
            String allowedOriginPatterns
    ) {
        this.stompAuthChannelInterceptor =
                stompAuthChannelInterceptor;
        this.webSocketHandshakeInterceptor =
                webSocketHandshakeInterceptor;
        this.webSocketHandshakeHandler =
                webSocketHandshakeHandler;
        this.stompHeartbeatTaskScheduler = stompHeartbeatTaskScheduler;
        this.stompOutboundExecutor = stompOutboundExecutor;
        this.environment = environment;

        this.allowedOriginPatterns =
                StringUtils
                        .commaDelimitedListToStringArray(
                                allowedOriginPatterns
                        );
    }

    /**
     * 生产环境不允许回退到开发环境的 localhost Origin 白名单。
     */
    @PostConstruct
    void validateProductionOriginConfiguration() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) && containsLocalDevelopmentOrigin()) {
                throw new IllegalStateException(
                        "生产环境必须通过 app.websocket.allowed-origin-patterns 配置明确的可信 Origin"
                );
            }
        }
    }

    private boolean containsLocalDevelopmentOrigin() {
        for (String pattern : allowedOriginPatterns) {
            if (!StringUtils.hasText(pattern)
                    || pattern.contains("localhost")
                    || pattern.contains("127.0.0.1")
                    || "*".equals(pattern.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 注册WebSocket握手地址。
     *
     * 非浏览器客户端可使用Authorization请求头；浏览器客户端先通过认证接口
     * 换取一次性短期ticket。正式访问Token不会出现在WebSocket URL中。
     * 握手阶段完成认证，
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
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(stompOutboundExecutor);
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.setPreservePublishOrder(true);
        registry.enableSimpleBroker(
                "/queue",
                "/topic"
        )
                .setHeartbeatValue(new long[]{10_000L, 10_000L})
                .setTaskScheduler(stompHeartbeatTaskScheduler);

        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }
}
