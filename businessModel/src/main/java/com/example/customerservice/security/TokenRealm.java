package com.example.customerservice.security;

import com.example.customerservice.constant.AccountStatus;

import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysRolePermissionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.TokenService;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;

import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;

import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import org.springframework.stereotype.Component;

import java.util.Set;


/**
 * Token认证和RBAC授权Realm。
 */
@Component
public class TokenRealm
        extends AuthorizingRealm {

    private final TokenService tokenService;

    private final SysUserMapper sysUserMapper;

    private final SysUserRoleMapper
            sysUserRoleMapper;

    private final SysRolePermissionMapper
            sysRolePermissionMapper;


    public TokenRealm(
            TokenService tokenService,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysRolePermissionMapper sysRolePermissionMapper
    ) {

        this.tokenService =
                tokenService;

        this.sysUserMapper =
                sysUserMapper;

        this.sysUserRoleMapper =
                sysUserRoleMapper;

        this.sysRolePermissionMapper =
                sysRolePermissionMapper;
    }


    /**
     * 当前Realm只处理TokenAuthToken。
     */
    @Override
    public boolean supports(
            AuthenticationToken token
    ) {

        return token
                instanceof TokenAuthToken;
    }


    /**
     * Token认证。
     */
    @Override
    protected AuthenticationInfo
    doGetAuthenticationInfo(
            AuthenticationToken authenticationToken
    ) throws AuthenticationException {

        TokenAuthToken tokenAuthToken =
                (TokenAuthToken)
                        authenticationToken;


        String token =
                tokenAuthToken.getToken();


        if (
                token == null ||
                        token.isBlank()
        ) {

            throw new IncorrectCredentialsException(
                    "Token不能为空"
            );
        }


        /*
         * 从Redis的token:{token}中解析userId。
         */
        String userId =
                tokenService.resolveUserId(
                        token
                );


        if (
                userId == null ||
                        userId.isBlank()
        ) {

            throw new IncorrectCredentialsException(
                    "Token无效或已经过期"
            );
        }


        /*
         * 确认用户仍然存在。
         */
        SysUser user =
                sysUserMapper.selectById(
                        userId
                );


        if (user == null) {

            throw new UnknownAccountException(
                    "Token对应的用户不存在"
            );
        }


        /*
         * 被禁用用户即使Token未过期，
         * 也不能继续访问系统。
         */
        if (
                !AccountStatus.ENABLED.equals(
                        user.getStatus()
                )
        ) {

            throw new DisabledAccountException(
                    "当前用户已被禁用"
            );
        }


        /*
         * principal使用真实userId。
         * credentials使用当前Token。
         */
        return new SimpleAuthenticationInfo(

                userId,

                token,

                getName()
        );
    }


    /**
     * 查询当前用户的角色和权限。
     */
    @Override
    protected AuthorizationInfo
    doGetAuthorizationInfo(
            PrincipalCollection principals
    ) {

        Object primaryPrincipal =
                principals.getPrimaryPrincipal();


        if (primaryPrincipal == null) {

            return new SimpleAuthorizationInfo();
        }


        String userId =
                primaryPrincipal.toString();


        Set<String> roleCodes =
                sysUserRoleMapper
                        .findRoleCodesByUserId(
                                userId
                        );


        Set<String> permissionCodes =
                sysRolePermissionMapper
                        .findPermissionCodesByUserId(
                                userId
                        );


        SimpleAuthorizationInfo authorizationInfo =
                new SimpleAuthorizationInfo();


        if (
                roleCodes != null &&
                        !roleCodes.isEmpty()
        ) {

            authorizationInfo.setRoles(
                    roleCodes
            );
        }


        if (
                permissionCodes != null &&
                        !permissionCodes.isEmpty()
        ) {

            authorizationInfo.setStringPermissions(
                    permissionCodes
            );
        }


        return authorizationInfo;
    }
}
