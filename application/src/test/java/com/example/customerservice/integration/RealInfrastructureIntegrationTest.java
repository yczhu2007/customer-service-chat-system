package com.example.customerservice.integration;

import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实MySQL和Redis验证基础设施及Lua原子操作。
 * 设置RUN_REAL_INTEGRATION_TESTS=true后参与测试，避免普通单测误连开发数据库。
 * 运行前必须已执行项目SQL完成建表和种子数据初始化；首次启动还必须设置
 * ADMIN_BOOTSTRAP_PASSWORD，现有数据库必须包含可用的ADMIN角色。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_INTEGRATION_TESTS",
        matches = "true"
)
class RealInfrastructureIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ChatRedisRepository chatRedisRepository;
    @Autowired private TokenService tokenService;

    @Test
    void mysqlRedisAndAtomicDeadLetterReplayAreAvailable() {
        Integer databaseResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, databaseResult);

        String deadLetterKey = "integration:test:deadletter";
        String pendingKey = "integration:test:pending";
        String payloadKey = "integration:test:payload:M001";
        try {
            redisTemplate.opsForValue().set(payloadKey, "payload");
            redisTemplate.opsForZSet().add(deadLetterKey, "M001", 1D);
            DefaultRedisScript<String> replayScript = new DefaultRedisScript<>(
                    "if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then "
                            + "return '__NOT_FOUND__'; end; "
                            + "local payload = redis.call('GET', KEYS[3]); "
                            + "if not payload then return '__PAYLOAD_EXPIRED__'; end; "
                            + "redis.call('ZREM', KEYS[1], ARGV[1]); "
                            + "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1]); "
                            + "return payload;",
                    String.class
            );

            String replayedPayload = redisTemplate.execute(
                    replayScript,
                    List.of(deadLetterKey, pendingKey, payloadKey),
                    "M001",
                    "2"
            );

            assertEquals("payload", replayedPayload);
            assertNull(redisTemplate.opsForZSet().score(deadLetterKey, "M001"));
            assertEquals(2D, redisTemplate.opsForZSet().score(pendingKey, "M001"));
        } finally {
            redisTemplate.delete(List.of(deadLetterKey, pendingKey, payloadKey));
        }
    }

    @Test
    void distributedLockCanBeRenewedAndReleasedByItsOwner() {
        String lockKey = "integration:test:scheduler-lock";
        redisTemplate.delete(lockKey);
        String token = null;
        try {
            token = chatRedisRepository.acquireLock(lockKey, 2, TimeUnit.SECONDS);
            assertNotNull(token);
            assertTrue(chatRedisRepository.renewLock(lockKey, token, 5));
            assertFalse(chatRedisRepository.renewLock(lockKey, "wrong-owner", 5));
        } finally {
            if (token != null) {
                chatRedisRepository.releaseLock(lockKey, token);
            }
        }
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey)));
    }

    @Test
    void tokenAndReverseIndexAreWrittenAtomicallyAsSortedSet() {
        String token = tokenService.issueToken("admin");
        try {
            assertEquals("admin", tokenService.resolveUserId(token));
            assertEquals(
                    DataType.ZSET,
                    redisTemplate.type(RedisConstants.userTokensKey("admin"))
            );
            assertNotNull(redisTemplate.opsForZSet().score(
                    RedisConstants.userTokensKey("admin"),
                    token
            ));
        } finally {
            tokenService.revokeToken(token);
        }
    }
}
