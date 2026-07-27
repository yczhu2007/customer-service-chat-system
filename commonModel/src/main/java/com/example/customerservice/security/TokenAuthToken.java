package com.example.customerservice.security;

import org.apache.shiro.authc.AuthenticationToken;


/**
 * Shiro无状态Token认证对象。
 */
public class TokenAuthToken
        implements AuthenticationToken {

    private final String token;


    public TokenAuthToken(
            String token
    ) {

        this.token =
                token;
    }


    public String getToken() {

        return token;
    }


    /**
     * 当前认证对象的身份凭证。
     *
     * Token尚未解析前，principal先使用Token本身。
     */
    @Override
    public Object getPrincipal() {

        return token;
    }


    /**
     * 当前认证对象的密码凭证。
     */
    @Override
    public Object getCredentials() {

        return token;
    }
}