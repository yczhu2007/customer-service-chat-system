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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;

/**
 * 聊天模块的 Redis 数据访问入口。
 *
 * <p>业务服务只依赖本类，不直接依赖 {@link StringRedisTemplate}。这样可以把 Redis
 * 客户端选择、数据结构访问和脚本执行集中在数据访问层，避免业务层同时承担基础设施职责。</p>
 */
@Repository
public class ChatRedisRepository {

    private static final ScheduledExecutorService LOCK_WATCHDOG =
            Executors.newScheduledThreadPool(2, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "chat-redis-lock-watchdog");
                    thread.setDaemon(true);
                    return thread;
                }
            });
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
    /**
     * 原子递增并设置TTL的Lua脚本。
     *
     * <p>用于消息发送频率限制，原子性地完成：
     * <ol>
     *   <li>对key执行INCR操作</li>
     *   <li>如果key是新创建的（值为1），则设置过期时间</li>
     * </ol>
     *
     * <p>参数说明：
     * <ul>
     *   <li>KEYS[1] - 限流key</li>
     *   <li>ARGV[1] - 过期时间（秒）</li>
     * </ul>
     *
     * <p>返回值：递增后的计数值
     */
    private static final DefaultRedisScript<Long> CANCEL_QUEUE_SCRIPT =
            new DefaultRedisScript<>(
                    "local removed = redis.call('ZREM', KEYS[1], ARGV[1]); "
                            + "redis.call('HDEL', KEYS[2], ARGV[1]); "
                            + "redis.call('HDEL', KEYS[3], ARGV[1]); "
                            + "redis.call('ZREM', KEYS[4], ARGV[1]); "
                            + "return removed;",
                    Long.class
            );
    private static final DefaultRedisScript<Long> INCR_AND_EXPIRE_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('INCR', KEYS[1]); "
                            + "if current == 1 then "
                            + "redis.call('EXPIRE', KEYS[1], ARGV[1]); "
                            + "end; "
                            + "return current;",
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

    public ScheduledFuture<?> startLockRenewal(String key, String token, long timeout, TimeUnit unit) {
        if (key == null || token == null || timeout <= 0) {
            return null;
        }
        long timeoutSeconds = Math.max(1L, unit.toSeconds(timeout));
        long intervalSeconds = Math.max(1L, timeoutSeconds / 3L);
        return LOCK_WATCHDOG.scheduleAtFixedRate(
                () -> renewLock(key, token, timeoutSeconds),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public void stopLockRenewal(ScheduledFuture<?> renewal) {
        if (renewal != null) {
            renewal.cancel(false);
        }
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

    public Long cancelQueueEntry(String userId) {
        return execute(
                CANCEL_QUEUE_SCRIPT,
                List.of(
                        RedisConstants.QUEUE_PENDING,
                        RedisConstants.QUEUE_ENQUEUED_AT,
                        RedisConstants.QUEUE_VIP_LEVEL,
                        RedisConstants.VIP_CALLBACK_PENDING
                ),
                userId
        );
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

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 原子递增并设置TTL（仅当key为新创建时）。
     *
     * <p>使用Lua脚本保证原子性，避免INCR和EXPIRE之间的竞态条件。
     * 适用于频率限制场景，确保计数器在首次递增时立即设置过期时间。</p>
     *
     * @param key 限流key
     * @param timeout 过期时间
     * @param unit 时间单位
     * @return 递增后的计数值，Redis不可用时返回null
     */
    public Long incrementAndExpire(String key, long timeout, TimeUnit unit) {
        long timeoutSeconds = unit.toSeconds(timeout);
        return execute(INCR_AND_EXPIRE_SCRIPT, java.util.Collections.singletonList(key), String.valueOf(timeoutSeconds));
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
