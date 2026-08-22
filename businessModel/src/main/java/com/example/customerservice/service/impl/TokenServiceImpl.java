package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.TokenService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.connection.DataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * 基于Redis的Token服务。
 */
@Service
public class TokenServiceImpl
        implements TokenService {

    private static final DefaultRedisScript<String> CONSUME_TICKET_SCRIPT =
            new DefaultRedisScript<>(
                    "local value = redis.call('GET', KEYS[1]); "
                            + "if value then redis.call('DEL', KEYS[1]); end; "
                            + "return value;",
                    String.class
            );
    private static final DefaultRedisScript<Long> ISSUE_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    "local indexType = redis.call('TYPE', KEYS[2])['ok']; "
                            + "if indexType == 'set' then "
                            + "local legacyTokens = redis.call('SMEMBERS', KEYS[2]); "
                            + "redis.call('DEL', KEYS[2]); "
                            + "for _, oldToken in ipairs(legacyTokens) do "
                            + "local remaining = redis.call('PTTL', ARGV[5] .. oldToken); "
                            + "if remaining > 0 then "
                            + "redis.call('ZADD', KEYS[2], ARGV[2] + remaining, oldToken); "
                            + "end; end; end; "
                            + "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[4]); "
                            + "redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2]); "
                            + "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[6]); "
                            + "local indexTtl = redis.call('TTL', KEYS[2]); "
                            + "if indexTtl < tonumber(ARGV[4]) then "
                            + "redis.call('EXPIRE', KEYS[2], ARGV[4]); end; "
                            + "return 1;",
                    Long.class
            );
    /**
     * 原子吊销Token的Lua脚本。
     *
     * <p>脚本逻辑：
     * <ol>
     *   <li>读取token:{token}对应的userId</li>
     *   <li>删除token:{token}键</li>
     *   <li>如果userId存在，构造user:tokens:{userId}键</li>
     *   <li>检查该索引的数据类型（兼容历史SET或当前ZSET）</li>
     *   <li>从索引中移除该token</li>
     * </ol>
     *
     * <p>参数说明：
     * <ul>
     *   <li>KEYS[1] - token:{token} 完整键</li>
     *   <li>ARGV[1] - token值（用于从索引中移除）</li>
     *   <li>ARGV[2] - user:tokens: 前缀</li>
     * </ul>
     *
     * <p>返回值：
     * <ul>
     *   <li>1 - 成功吊销（token存在且已删除）</li>
     *   <li>0 - token不存在或已过期</li>
     * </ul>
     */
    private static final DefaultRedisScript<Long> REVOKE_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    "local userId = redis.call('GET', KEYS[1]); "
                            + "if not userId then return 0; end; "
                            + "redis.call('DEL', KEYS[1]); "
                            + "local userTokensKey = ARGV[2] .. userId; "
                            + "local indexType = redis.call('TYPE', userTokensKey)['ok']; "
                            + "if indexType == 'set' then "
                            + "redis.call('SREM', userTokensKey, ARGV[1]); "
                            + "elseif indexType == 'zset' then "
                            + "redis.call('ZREM', userTokensKey, ARGV[1]); "
                            + "end; "
                            + "return 1;",
                    Long.class
            );

    private final StringRedisTemplate
            redisTemplate;
    private final Duration tokenTtl;
    private final Duration rememberTokenTtl;


    public TokenServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.token-ttl-minutes:30}") long tokenTtlMinutes,
            @Value("${app.auth.remember-token-ttl-days:7}") long rememberTokenTtlDays
    ) {

        this.redisTemplate =
                redisTemplate;
        if (tokenTtlMinutes <= 0) {
            throw new IllegalArgumentException("Token有效期必须大于0分钟");
        }
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
        if (rememberTokenTtlDays <= 0) {
            throw new IllegalArgumentException("记住我Token有效期必须大于0天");
        }
        this.rememberTokenTtl = Duration.ofDays(rememberTokenTtlDays);
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
    public String issueToken(String userId, boolean rememberMe) {

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


        Duration effectiveTtl = rememberMe ? rememberTokenTtl : tokenTtl;
        String userTokensKey = RedisConstants.userTokensKey(userId);
        long now = System.currentTimeMillis();
        Long issued = redisTemplate.execute(
                ISSUE_TOKEN_SCRIPT,
                List.of(
                        RedisConstants.tokenKey(token),
                        userTokensKey
                ),
                userId,
                String.valueOf(now),
                String.valueOf(now + effectiveTtl.toMillis()),
                String.valueOf(effectiveTtl.toSeconds()),
                RedisConstants.TOKEN_PREFIX,
                token
        );
        if (!Long.valueOf(1L).equals(issued)) {
            throw new IllegalStateException("Token签发状态写入失败");
        }


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
    public long getTokenTtlSeconds(boolean rememberMe) {
        return rememberMe ? rememberTokenTtl.toSeconds() : tokenTtl.toSeconds();
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


        String tokenKey = RedisConstants.tokenKey(token);
        // 使用Lua脚本原子执行：读取userId、删除token键、从用户索引中移除token
        // 脚本会自动处理SET或ZSET两种索引类型（兼容历史数据）
        redisTemplate.execute(
                REVOKE_TOKEN_SCRIPT,
                List.of(tokenKey),
                token,
                RedisConstants.USER_TOKENS_PREFIX
        );
    }

    @Override
    public void revokeAllForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String userTokensKey = RedisConstants.userTokensKey(userId);
        migrateLegacyTokenIndex(userTokensKey, System.currentTimeMillis());
        redisTemplate.opsForZSet().removeRangeByScore(
                userTokensKey,
                0,
                System.currentTimeMillis()
        );
        Set<String> tokens = redisTemplate.opsForZSet().range(
                userTokensKey,
                0,
                -1
        );
        if (tokens != null && !tokens.isEmpty()) {
            List<String> tokenKeys = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                tokenKeys.add(RedisConstants.tokenKey(token));
            }
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(userTokensKey);
    }

    private void migrateLegacyTokenIndex(String userTokensKey, long now) {
        if (!DataType.SET.equals(redisTemplate.type(userTokensKey))) {
            return;
        }
        Set<String> legacyTokens = redisTemplate.opsForSet().members(userTokensKey);
        redisTemplate.delete(userTokensKey);
        if (legacyTokens == null || legacyTokens.isEmpty()) {
            return;
        }
        for (String legacyToken : legacyTokens) {
            Long remainingMillis = redisTemplate.getExpire(
                    RedisConstants.tokenKey(legacyToken),
                    TimeUnit.MILLISECONDS
            );
            if (remainingMillis != null && remainingMillis > 0L) {
                redisTemplate.opsForZSet().add(
                        userTokensKey,
                        legacyToken,
                        now + remainingMillis
                );
            }
        }
    }

    @Override
    public String issueWebSocketTicket(String accessToken, String userId) {
        if (accessToken == null || accessToken.isBlank()
                || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("访问Token和userId不能为空");
        }
        String resolvedUserId = resolveUserId(accessToken);
        if (!userId.equals(resolvedUserId)) {
            throw new IllegalArgumentException("访问Token与当前用户不匹配");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                RedisConstants.webSocketTicketKey(ticket),
                accessToken,
                Duration.ofSeconds(RedisConstants.WEBSOCKET_TICKET_TTL_SECONDS)
        );
        return ticket;
    }

    @Override
    public String consumeWebSocketTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        return redisTemplate.execute(
                CONSUME_TICKET_SCRIPT,
                List.of(RedisConstants.webSocketTicketKey(ticket))
        );
    }
}
