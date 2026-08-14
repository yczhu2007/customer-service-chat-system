package com.example.customerservice.repository;

import com.example.customerservice.constant.RedisConstants;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 聊天模块的 Redis 数据访问入口。
 *
 * <p>业务服务只依赖本类，不直接依赖 {@link StringRedisTemplate}。这样可以把 Redis
 * 客户端选择、数据结构访问和脚本执行集中在数据访问层，避免业务层同时承担基础设施职责。</p>
 */
@Repository
public class ChatRedisRepository {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('DEL', KEYS[1]); "
                            + "else return 0; end;",
                    Long.class
            );
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('EXPIRE', KEYS[1], ARGV[2]); "
                            + "else return 0; end;",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public ChatRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setValue(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Boolean setValueIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    public String acquireLock(String key, long timeout, TimeUnit unit) {
        String token = java.util.UUID.randomUUID().toString();
        Boolean acquired = setValueIfAbsent(key, token, timeout, unit);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void releaseLock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        execute(RELEASE_LOCK_SCRIPT, java.util.Collections.singletonList(key), token);
    }

    public boolean renewLock(String key, String token, long timeoutSeconds) {
        if (key == null || token == null || timeoutSeconds <= 0) {
            return false;
        }
        Long renewed = execute(
                RENEW_LOCK_SCRIPT,
                java.util.Collections.singletonList(key),
                token,
                String.valueOf(timeoutSeconds)
        );
        return Long.valueOf(1L).equals(renewed);
    }

    public String acquireSessionOperationLock(String sessionId) {
        return acquireLock(
                RedisConstants.SESSION_OPERATION_LOCK + sessionId,
                RedisConstants.SESSION_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void releaseSessionOperationLock(String sessionId, String token) {
        releaseLock(RedisConstants.SESSION_OPERATION_LOCK + sessionId, token);
    }

    public Object hashGet(String key, Object field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public void hashPut(String key, Object field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Long hashDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    public Long setAdd(String key, String... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    public Long setRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    public Boolean setIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Set<String> setMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public List<String> listRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public Long listRightPush(String key, String value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public Long listRemove(String key, long count, Object value) {
        return redisTemplate.opsForList().remove(key, count, value);
    }

    public void listSet(String key, long index, String value) {
        redisTemplate.opsForList().set(key, index, value);
    }

    public void listTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    public Boolean sortedSetAdd(String key, String value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    public Long sortedSetRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    public Double sortedSetScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    public Set<String> sortedSetRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Set<String> sortedSetRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    public Set<String> sortedSetRangeByScore(
            String key,
            double min,
            double max,
            long offset,
            long count
    ) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max, offset, count);
    }

    public Long sortedSetRank(String key, Object value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    public Long sortedSetCardinality(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public DataType type(String key) {
        return redisTemplate.type(key);
    }

    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }
}
