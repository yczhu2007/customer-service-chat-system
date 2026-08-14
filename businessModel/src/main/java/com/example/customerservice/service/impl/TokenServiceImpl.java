package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.TokenService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;


/**
 * 基于Redis的Token服务。
 */
@Service
public class TokenServiceImpl
        implements TokenService {

    private final StringRedisTemplate
            redisTemplate;
    private final Duration tokenTtl;


    public TokenServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.token-ttl-minutes:30}") long tokenTtlMinutes
    ) {

        this.redisTemplate =
                redisTemplate;
        if (tokenTtlMinutes <= 0) {
            throw new IllegalArgumentException("Token有效期必须大于0分钟");
        }
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }


    /**
     * 签发随机Token。
     *
     * Redis：
     *
     * key：
     * token:{token}
     *
     * value：
     * userId
     *
     * TTL：
     * 30分钟
     */
    @Override
    public String issueToken(
            String userId
    ) {

        if (
                userId == null ||
                        userId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }


        String token =
                UUID.randomUUID()
                        .toString()
                        .replace(
                                "-",
                                ""
                        );


        redisTemplate
                .opsForValue()
                .set(

                        RedisConstants
                                .tokenKey(
                                        token
                                ),

                        userId,

                        tokenTtl
                );


        return token;
    }


    @Override
    public String resolveUserId(
            String token
    ) {

        if (
                token == null ||
                        token.isBlank()
        ) {

            return null;
        }


        return redisTemplate
                .opsForValue()
                .get(
                        RedisConstants
                                .tokenKey(
                                        token
                                )
                );
    }


    @Override
    public boolean isValid(
            String token
    ) {

        return resolveUserId(
                token
                ) != null;
    }

    @Override
    public long getTokenTtlSeconds() {
        return tokenTtl.toSeconds();
    }


    @Override
    public void revokeToken(
            String token
    ) {

        if (
                token == null ||
                        token.isBlank()
        ) {

            return;
        }


        redisTemplate.delete(
                RedisConstants
                        .tokenKey(
                                token
                        )
        );
    }
}
