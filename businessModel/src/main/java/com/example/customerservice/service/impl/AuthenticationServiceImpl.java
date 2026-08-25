package com.example.customerservice.service.impl;

import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.LoginRequest;
import com.example.customerservice.dto.LoginResponse;
import com.example.customerservice.dto.WebSocketTicketResponse;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.mapper.SysRolePermissionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.TokenService;
import com.example.customerservice.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * 统一处理认证业务，Controller 不直接访问 Mapper。
 */
@Service
public class AuthenticationServiceImpl implements IAuthenticationService {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Autowired
    private TokenService tokenService;

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户名和密码不能为空"
            );
        }

        SysUser user = sysUserMapper.findByUsername(
                request.getUsername().trim()
        );
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        if (!PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        if (!"ENABLED".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前用户已被禁用"
            );
        }

        Set<String> roleCodes = findRoleCodesByUserId(user.getId());
        String token = tokenService.issueToken(user.getId(), request.isRememberMe());
        return new LoginResponse(
                token,
                "Bearer",
                tokenService.getTokenTtlSeconds(request.isRememberMe()),
                user.getId(),
                user.getUsername(),
                displayName(user),
                roleCodes
        );
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    @Override
    public void logout(String authorization, String currentUserId) {
        String accessToken = requireCurrentAccessToken(authorization, currentUserId);
        tokenService.revokeToken(accessToken);
    }

    @Override
    public WebSocketTicketResponse issueWebSocketTicket(
            String authorization,
            String currentUserId
    ) {
        String accessToken = requireCurrentAccessToken(authorization, currentUserId);
        String ticket = tokenService.issueWebSocketTicket(
                accessToken,
                currentUserId
        );
        return new WebSocketTicketResponse(
                ticket,
                RedisConstants.WEBSOCKET_TICKET_TTL_SECONDS
        );
    }

    @Override
    public Set<String> findRoleCodesByUserId(String userId) {
        Set<String> roleCodes = sysUserRoleMapper.findRoleCodesByUserId(userId);
        return roleCodes == null ? Set.of() : roleCodes;
    }

    @Override
    public void requireRole(String userId, String roleCode) {
        if (!findRoleCodesByUserId(userId).contains(roleCode)) {
            throw new IllegalArgumentException("当前用户缺少角色：" + roleCode);
        }
    }

    @Override
    public void requirePermission(String userId, String permissionCode) {
        Set<String> permissionCodes = sysRolePermissionMapper
                .findPermissionCodesByUserId(userId);
        if (permissionCodes == null || !permissionCodes.contains(permissionCode)) {
            throw new IllegalArgumentException("当前用户缺少权限：" + permissionCode);
        }
    }

    private String requireCurrentAccessToken(
            String authorization,
            String currentUserId
    ) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Authorization必须使用Bearer Token");
        }
        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        String resolvedUserId = tokenService.resolveUserId(accessToken);
        if (accessToken.isBlank() || !currentUserId.equals(resolvedUserId)) {
            throw new IllegalArgumentException("访问Token与当前用户不匹配");
        }
        return accessToken;
    }
}
