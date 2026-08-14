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

    /** 当前登录Token的有效期，单位为秒。 */
    long getTokenTtlSeconds();


    /**
     * 注销Token。
     */
    void revokeToken(
            String token
    );

    /** 吊销某个用户当前签发的全部Token。 */
    void revokeAllForUser(String userId);

    /** 为已经认证的访问Token签发一次性WebSocket握手票据。 */
    String issueWebSocketTicket(String accessToken, String userId);

    /** 原子消费一次性票据并返回其绑定的访问Token。 */
    String consumeWebSocketTicket(String ticket);
}
