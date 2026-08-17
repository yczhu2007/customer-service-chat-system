package com.example.customerservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimiterTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loginUsesIndependentBucketAndReturns429AfterLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), anyString()
        )).thenReturn(6L);
        AuthRateLimiter limiter = new AuthRateLimiter(redisTemplate, 5, 60, false);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> limiter.checkLogin(request)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("auth:login:rate:127.0.0.1")),
                eq("60")
        );
    }
}
