package com.example.customerservice.security;

import com.example.customerservice.constant.RoleCodes;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WorkspaceRoleResolver {
    public String resolve(String header, Set<String> roles, String... allowed) {
        String role = header == null || header.isBlank() ? null : header;
        if (role == null) {
            return roles.contains(RoleCodes.ADMIN) ? RoleCodes.ADMIN
                    : (roles.contains(RoleCodes.AGENT) ? RoleCodes.AGENT : RoleCodes.USER);
        }
        boolean accepted = false;
        for (String candidate : allowed) accepted |= candidate.equals(role);
        if (!accepted || !roles.contains(role)) throw new IllegalArgumentException("当前工作台角色无效");
        return role;
    }
}
