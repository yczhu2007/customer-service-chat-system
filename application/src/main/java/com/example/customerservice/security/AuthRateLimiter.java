package com.example.customerservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/** 基于客户端地址限制匿名认证接口的调用频率。 */
@Component
public class AuthRateLimiter {
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );
    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final Duration window;
    private final boolean trustForwardedHeaders;

    public AuthRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.rate-limit.max-requests:5}") int maxRequests,
            @Value("${app.auth.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.auth.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = Math.max(1, Math.min(maxRequests, 100));
        this.window = Duration.ofSeconds(Math.max(10, Math.min(windowSeconds, 3600)));
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public void checkRegistration(HttpServletRequest request) {
        check(request, "auth:register:rate:", "注册操作过于频繁，请稍后再试");
    }

    public void checkPasswordReset(HttpServletRequest request) {
        check(request, "auth:password-reset:rate:", "密码重置操作过于频繁，请稍后再试");
    }

    public void checkLogin(HttpServletRequest request) {
        check(request, "auth:login:rate:", "登录尝试过于频繁，请稍后再试");
    }

    private void check(HttpServletRequest request, String keyPrefix, String message) {
        String remoteAddress = resolveClientAddress(request);
        String normalizedAddress = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown" : remoteAddress.replaceAll("[^0-9A-Fa-f:.]", "_");
        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(keyPrefix + normalizedAddress),
                Long.toString(window.toSeconds())
        );
        if (count != null && count > maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private String resolveClientAddress(HttpServletRequest request) {
        if (request == null) return "unknown";
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
