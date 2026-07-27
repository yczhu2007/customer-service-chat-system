package com.example.customerservice.config;

import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysRolePermissionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.TokenService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;

/**
 * STOMP身份认证和订阅权限校验。
 *
 * CONNECT帧负责建立认证Principal；
 * SEND和SUBSCRIBE帧会重新检查Token有效性。
 */
@Component
public class StompAuthChannelInterceptor
        implements ChannelInterceptor {

    private static final String CHAT_QUEUE =
            "/user/queue/chat";

    private final TokenService tokenService;

    private final SysUserMapper sysUserMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysRolePermissionMapper
            sysRolePermissionMapper;

    public StompAuthChannelInterceptor(
            TokenService tokenService,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysRolePermissionMapper
                    sysRolePermissionMapper
    ) {
        this.tokenService = tokenService;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper =
                sysUserRoleMapper;
        this.sysRolePermissionMapper =
                sysRolePermissionMapper;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        StompCommand command =
                accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            authenticateConnect(
                    accessor
            );
            return message;
        }

        if (
                command != StompCommand.SEND &&
                        command
                                != StompCommand.SUBSCRIBE
        ) {
            return message;
        }

        WebSocketUserPrincipal principal =
                requireCurrentPrincipal(
                        accessor.getUser()
                );

        String userId =
                requireValidToken(
                        principal
                );

        if (
                command == StompCommand.SUBSCRIBE &&
                        CHAT_QUEUE.equals(
                                accessor.getDestination()
                        )
        ) {
            requireChatSubscriptionPermission(
                    userId
            );
        }

        return message;
    }

    private void authenticateConnect(
            StompHeaderAccessor accessor
    ) {
        WebSocketUserPrincipal principal =
                requireCurrentPrincipal(
                        accessor.getUser()
                );

        /*
         * Token已经在HTTP握手阶段校验。
         * CONNECT阶段再次确认Token仍有效，防止握手后立即失效。
         */
        requireValidToken(
                principal
        );

        accessor.setUser(
                principal
        );
    }

    private WebSocketUserPrincipal
    requireCurrentPrincipal(
            Principal principal
    ) {
        if (
                !(principal
                        instanceof WebSocketUserPrincipal)
        ) {
            throw new MessageDeliveryException(
                    "当前STOMP连接没有有效用户身份"
            );
        }

        return (WebSocketUserPrincipal)
                principal;
    }

    private String requireValidToken(
            WebSocketUserPrincipal principal
    ) {
        String userId =
                tokenService.resolveUserId(
                        principal.getToken()
                );

        if (
                userId == null ||
                        !userId.equals(
                                principal.getName()
                        )
        ) {
            throw new MessageDeliveryException(
                    "Token无效或已过期，请重新登录并连接"
            );
        }

        requireEnabledUser(
                userId
        );

        return userId;
    }

    private SysUser requireEnabledUser(
            String userId
    ) {
        SysUser user =
                sysUserMapper.selectById(
                        userId
                );

        if (
                user == null ||
                        !"ENABLED".equals(
                                user.getStatus()
                        )
        ) {
            throw new MessageDeliveryException(
                    "当前用户不存在或已被禁用"
            );
        }

        return user;
    }

    private void requireChatSubscriptionPermission(
            String userId
    ) {
        Set<String> roleCodes =
                sysUserRoleMapper
                        .findRoleCodesByUserId(
                                userId
                        );

        if (
                roleCodes != null &&
                        (
                                roleCodes.contains(
                                        "AGENT"
                                ) ||
                                        roleCodes.contains(
                                                "ADMIN"
                                        )
                        )
        ) {
            return;
        }

        if (
                roleCodes == null ||
                        !roleCodes.contains(
                                "USER"
                        )
        ) {
            throw new MessageDeliveryException(
                    "当前用户角色不允许订阅聊天队列"
            );
        }

        Set<String> permissionCodes =
                sysRolePermissionMapper
                        .findPermissionCodesByUserId(
                                userId
                        );

        if (
                permissionCodes == null ||
                        !permissionCodes.contains(
                                "chat:user:access"
                        )
        ) {
            throw new MessageDeliveryException(
                    "当前用户缺少聊天接入权限"
            );
        }
    }
}
