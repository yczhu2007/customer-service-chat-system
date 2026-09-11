package com.example.customerservice.config;

import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Set;

/**
 * 在WebSocket HTTP握手阶段校验Token并建立用户Principal。
 */
@Component
public class WebSocketHandshakeInterceptor
        implements HandshakeInterceptor {

    public static final String AUTHENTICATED_PRINCIPAL =
            WebSocketHandshakeInterceptor.class.getName()
                    + ".AUTHENTICATED_PRINCIPAL";

    /** 仅供服务端逐帧复验访问令牌的会话属性。 */
    static final String ACCESS_TOKEN_ATTRIBUTE =
            WebSocketHandshakeInterceptor.class.getName()
                    + ".ACCESS_TOKEN";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final TokenService tokenService;
    private final IAuthenticationService authenticationService;

    public WebSocketHandshakeInterceptor(
            TokenService tokenService,
            IAuthenticationService authenticationService
    ) {
        this.tokenService = tokenService;
        this.authenticationService = authenticationService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = extractAccessToken(request);

        if (token == null || token.isBlank()) {
            response.setStatusCode(
                    HttpStatus.UNAUTHORIZED
            );
            return false;
        }

        String userId =
                tokenService.resolveUserId(
                        token
                );

        if (userId == null || userId.isBlank()) {
            response.setStatusCode(
                    HttpStatus.UNAUTHORIZED
            );
            return false;
        }

        try {
            authenticationService.requireEnabledUser(userId);
        } catch (IllegalArgumentException exception) {
            response.setStatusCode(
                    HttpStatus.UNAUTHORIZED
            );
            return false;
        }

        Set<String> roleCodes = authenticationService.findRoleCodesByUserId(userId);

        // 令牌仅保存在服务端会话属性中，不放入 Principal。
        attributes.put(ACCESS_TOKEN_ATTRIBUTE, token);
        attributes.put(
                AUTHENTICATED_PRINCIPAL,
                new WebSocketUserPrincipal(
                        userId,
                        roleCodes
                )
        );

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // 握手完成后无需额外处理。
    }

    private String extractAccessToken(
            ServerHttpRequest request
    ) {
        String authorization =
                request.getHeaders()
                        .getFirst(
                                "Authorization"
                        );

        if (
                authorization != null &&
                        authorization.startsWith(
                                BEARER_PREFIX
                        )
        ) {
            return authorization.substring(
                    BEARER_PREFIX.length()
            ).trim();
        }

        if (
                request
                        instanceof ServletServerHttpRequest
                        servletRequest
        ) {
            String ticket =
                    servletRequest
                            .getServletRequest()
                            .getParameter(
                                    "ticket"
                            );

            if (ticket != null && !ticket.isBlank()) {
                return tokenService.consumeWebSocketTicket(ticket.trim());
            }
        }

        return null;
    }
}
