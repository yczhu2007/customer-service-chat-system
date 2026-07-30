package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.LoginDTO;
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

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Autowired
    private TokenService tokenService;

    @Override
    public LoginDTO login(LoginDTO request) {
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

        if (PasswordUtil.needsUpgrade(user.getPassword())) {
            String hashedPassword = PasswordUtil.hash(request.getPassword());
            if (sysUserMapper.updatePassword(user.getId(), hashedPassword) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "密码安全升级失败"
                );
            }
        }

        Set<String> roleCodes = findRoleCodesByUserId(user.getId());
        String token = tokenService.issueToken(user.getId());
        return LoginDTO.success(
                token,
                "Bearer",
                RedisConstants.TOKEN_TTL_MINUTES * 60,
                user.getId(),
                user.getUsername(),
                roleCodes
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
}
