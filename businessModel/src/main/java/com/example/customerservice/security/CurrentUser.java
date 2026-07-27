package com.example.customerservice.security;

import com.example.customerservice.mapper.SysRolePermissionMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;

import org.springframework.stereotype.Component;

import java.util.Set;


/**
 * 获取当前已经认证的用户。
 */
@Component
public class CurrentUser {

    private final SysUserRoleMapper
            sysUserRoleMapper;

    private final SysRolePermissionMapper
            sysRolePermissionMapper;


    public CurrentUser(
            SysUserRoleMapper sysUserRoleMapper,
            SysRolePermissionMapper sysRolePermissionMapper
    ) {

        this.sysUserRoleMapper =
                sysUserRoleMapper;

        this.sysRolePermissionMapper =
                sysRolePermissionMapper;
    }


    /**
     * 获取当前登录用户ID。
     */
    public String getUserId() {

        Subject subject =
                SecurityUtils.getSubject();


        Object principal =
                subject.getPrincipal();


        if (principal == null) {

            throw new UnauthorizedException(
                    "当前用户尚未登录"
            );
        }


        return principal.toString();
    }


    /**
     * 获取当前用户角色。
     */
    public Set<String> getRoleCodes() {

        Set<String> roleCodes =
                sysUserRoleMapper
                        .findRoleCodesByUserId(
                                getUserId()
                        );


        if (roleCodes == null) {

            return Set.of();
        }


        return roleCodes;
    }


    /**
     * 获取当前用户权限。
     */
    public Set<String> getPermissionCodes() {

        Set<String> permissionCodes =
                sysRolePermissionMapper
                        .findPermissionCodesByUserId(
                                getUserId()
                        );


        if (permissionCodes == null) {

            return Set.of();
        }


        return permissionCodes;
    }


    /**
     * 判断当前用户是否拥有指定角色。
     */
    public boolean hasRole(
            String roleCode
    ) {

        return SecurityUtils
                .getSubject()
                .hasRole(
                        roleCode
                );
    }


    /**
     * 判断当前用户是否拥有指定权限。
     */
    public boolean hasPermission(
            String permissionCode
    ) {

        return SecurityUtils
                .getSubject()
                .isPermitted(
                        permissionCode
                );
    }


    /**
     * 要求当前用户必须拥有指定角色。
     */
    public void requireRole(
            String roleCode
    ) {

        if (!hasRole(roleCode)) {

            throw new UnauthorizedException(
                    "当前用户缺少角色："
                            + roleCode
            );
        }
    }


    /**
     * 要求当前用户必须拥有指定权限。
     */
    public void requirePermission(
            String permissionCode
    ) {

        if (
                !hasPermission(
                        permissionCode
                )
        ) {

            throw new UnauthorizedException(
                    "当前用户缺少权限："
                            + permissionCode
            );
        }
    }
}