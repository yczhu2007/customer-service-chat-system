package com.example.customerservice.service;

import com.example.customerservice.dto.LoginRequest;
import com.example.customerservice.dto.LoginResponse;

import java.util.Set;

/**
 * 认证、登录及 WebSocket RBAC 校验服务。
 */
public interface IAuthenticationService {

    LoginResponse login(LoginRequest request);

    Set<String> findRoleCodesByUserId(String userId);

    void requireRole(String userId, String roleCode);

    void requirePermission(String userId, String permissionCode);
}
