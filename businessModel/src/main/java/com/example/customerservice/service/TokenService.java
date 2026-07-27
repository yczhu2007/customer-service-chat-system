package com.example.customerservice.service;


/**
 * 登录Token服务。
 */
public interface TokenService {

    /**
     * 为用户签发Token。
     */
    String issueToken(
            String userId
    );


    /**
     * 根据Token解析用户ID。
     *
     * 不存在或过期时返回null。
     */
    String resolveUserId(
            String token
    );


    /**
     * 判断Token是否有效。
     */
    boolean isValid(
            String token
    );


    /**
     * 注销Token。
     */
    void revokeToken(
            String token
    );
}