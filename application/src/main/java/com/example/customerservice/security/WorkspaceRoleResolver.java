package com.example.customerservice.security;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WorkspaceRoleResolver {
    public String resolve(String header, Set<String> roles, String... allowed) {
        String role = header == null || header.isBlank() ? null : header;
        if (role == null) {
            return roles.contains("ADMIN") ? "ADMIN" : (roles.contains("AGENT") ? "AGENT" : "USER");
        }
        boolean accepted = false;
        for (String candidate : allowed) accepted |= candidate.equals(role);
        if (!accepted || !roles.contains(role)) throw new IllegalArgumentException("当前工作台角色无效");
        return role;
    }
}
