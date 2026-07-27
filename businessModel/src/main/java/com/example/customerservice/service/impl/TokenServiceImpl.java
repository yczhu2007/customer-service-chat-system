package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.TokenService;

import org.springframework.data.redis.core.StringRedisTemplate;
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


    public TokenServiceImpl(
            StringRedisTemplate redisTemplate
    ) {

        this.redisTemplate =
                redisTemplate;
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

                        Duration.ofMinutes(
                                RedisConstants
                                        .TOKEN_TTL_MINUTES
                        )
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