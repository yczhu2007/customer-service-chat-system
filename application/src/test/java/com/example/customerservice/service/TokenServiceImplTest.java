package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.impl.TokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void issuedTokenAlwaysHasConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15);

        String token = tokenService.issueToken("U001");

        assertFalse(token.isBlank());
        assertEquals(900L, tokenService.getTokenTtlSeconds());
        verify(valueOperations).set(
                eq(RedisConstants.tokenKey(token)),
                eq("U001"),
                eq(Duration.ofMinutes(15))
        );
    }
}
