package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.impl.TokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void issuedTokenAlwaysHasConfiguredTtl() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()
        )).thenReturn(1L);
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        String token = tokenService.issueToken("U001");

        assertFalse(token.isBlank());
        assertEquals(900L, tokenService.getTokenTtlSeconds(false));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        RedisConstants.tokenKey(token),
                        RedisConstants.userTokensKey("U001")
                )),
                eq("U001"), anyString(), anyString(),
                eq("900"), eq(RedisConstants.TOKEN_PREFIX), eq(token)
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rememberMeTokenUsesLongerConfiguredTtl() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()
        )).thenReturn(1L);
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        String token = tokenService.issueToken("U001", true);

        assertEquals(604800L, tokenService.getTokenTtlSeconds(true));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisConstants.tokenKey(token), RedisConstants.userTokensKey("U001"))),
                eq("U001"), anyString(), anyString(), eq("604800"),
                eq(RedisConstants.TOKEN_PREFIX), eq(token)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokesEveryTokenIssuedForUser() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.range(RedisConstants.userTokensKey("U001"), 0, -1))
                .thenReturn(Set.of("T001", "T002"));
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        tokenService.revokeAllForUser("U001");

        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertTrue(keysCaptor.getValue().containsAll(Set.of(
                RedisConstants.tokenKey("T001"),
                RedisConstants.tokenKey("T002")
        )));
        verify(redisTemplate).delete(RedisConstants.userTokensKey("U001"));
    }

    @Test
    void webSocketTicketIsShortLivedAndBoundToCurrentAccessToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisConstants.tokenKey("ACCESS")))
                .thenReturn("U001");
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        String ticket = tokenService.issueWebSocketTicket("ACCESS", "U001");

        assertNotNull(ticket);
        verify(valueOperations).set(
                RedisConstants.webSocketTicketKey(ticket),
                "ACCESS",
                Duration.ofSeconds(RedisConstants.WEBSOCKET_TICKET_TTL_SECONDS)
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void revokeTokenExecutesAtomicLuaScript() {
        // 模拟Lua脚本返回1表示成功吊销
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(), anyString()
        )).thenReturn(1L);
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        tokenService.revokeToken("VALID_TOKEN");

        // 验证调用了Lua脚本，传入正确的key和参数
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisConstants.tokenKey("VALID_TOKEN"))),
                eq("VALID_TOKEN"),
                eq(RedisConstants.USER_TOKENS_PREFIX)
        );
    }

    @Test
    void revokeTokenWithNullTokenDoesNothing() {
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        // 不应该抛出异常，也不应该调用Redis
        tokenService.revokeToken(null);
        tokenService.revokeToken("");
        tokenService.revokeToken("   ");

        // 验证没有调用execute方法
        verify(redisTemplate, org.mockito.Mockito.never()).execute(
                any(RedisScript.class),
                anyList(),
                any()
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void revokeTokenHandlesNonExistentTokenGracefully() {
        // 模拟Lua脚本返回0表示token不存在
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(), anyString()
        )).thenReturn(0L);
        TokenServiceImpl tokenService = new TokenServiceImpl(redisTemplate, 15, 7);

        // 不应该抛出异常
        tokenService.revokeToken("NON_EXISTENT_TOKEN");

        // 验证仍然调用了Lua脚本
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisConstants.tokenKey("NON_EXISTENT_TOKEN"))),
                eq("NON_EXISTENT_TOKEN"),
                eq(RedisConstants.USER_TOKENS_PREFIX)
        );
    }
}
