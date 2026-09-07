package com.example.customerservice.service.impl;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis Lua scripts used by routing and session lifecycle services. */
final class ChatRedisScripts {

    private ChatRedisScripts() {
    }

    static final DefaultRedisScript<String>
            RESERVE_IDLE_AGENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local maxLoad = tonumber(ARGV[1]); " +
                            "local isVip = ARGV[4] == '1'; " +
                            "local reserved = tonumber(ARGV[5]); " +
                            "local excludedAgent = ARGV[6]; " +
                            "local vipAgentCount = redis.call('SCARD', KEYS[4]); " +
                            "local agents = redis.call('ZRANGEBYSCORE', KEYS[1], 0, maxLoad - 1); " +
                            "local preferVipSkill = isVip and vipAgentCount > 0; " +
                            "local selected = nil; local selectedLoad = nil; local selectedAt = nil; " +
                            "for _, agent in ipairs(agents) do " +
                            "local load = tonumber(redis.call('ZSCORE', KEYS[1], agent)); " +
                            "local vipSkilled = redis.call('SISMEMBER', KEYS[4], agent) == 1; " +
                            "local eligible = agent ~= excludedAgent; " +
                            "if preferVipSkill and not vipSkilled then eligible = false; end; " +
                            "if not isVip and vipSkilled and load >= math.max(0, maxLoad - reserved) then eligible = false; end; " +
                            "if eligible then " +
                            "local assignedAt = tonumber(redis.call('ZSCORE', KEYS[5], agent) or '0'); " +
                            "if not selected or load < selectedLoad or (load == selectedLoad and assignedAt < selectedAt) then " +
                            "selected = agent; selectedLoad = load; selectedAt = assignedAt; end; end; " +
                            "end; " +
                            "if not selected then return nil; end; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, selected); " +
                            "redis.call('ZADD', KEYS[5], ARGV[3], selected); " +
                            "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2]); " +
                            "redis.call('HSET', KEYS[3], ARGV[2], selected .. '|' .. ARGV[3] .. '|' .. ARGV[3]); " +
                            "return selected;",
                    String.class
            );

    static final DefaultRedisScript<Long>
            RELEASE_ASSIGNMENT_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('DEL', KEYS[1]); " +
                            "else return 0; end;",
                    Long.class
            );

    /**
     * 原子执行“检查客服容量 + 队首出队 + 增加客服负载”。
     * 会话落库失败时调用方会将用户重新入队并释放这一次负载。
     */
    static final DefaultRedisScript<String>
            RESERVE_AGENT_AND_DEQUEUE_SCRIPT =
            new DefaultRedisScript<>(
                            "local score = redis.call('ZSCORE', KEYS[1], ARGV[1]); " +
                            "local maxLoad = tonumber(ARGV[2]); " +
                            "if not score or tonumber(score) >= maxLoad then return nil; end; " +
                            "local first = redis.call('ZRANGEBYSCORE', KEYS[9], '-inf', ARGV[3], 'LIMIT', 0, 1); " +
                            "if #first > 0 and not redis.call('ZSCORE', KEYS[2], first[1]) then redis.call('ZREM', KEYS[9], first[1]); first = {}; end; " +
                            "if #first == 0 then first = redis.call('ZRANGE', KEYS[2], 0, 0); end; " +
                            "if #first == 0 then return nil; end; " +
                            "local vipLevel = tonumber(redis.call('HGET', KEYS[6], first[1]) or '0'); " +
                            "local vipSkilled = redis.call('SISMEMBER', KEYS[7], ARGV[1]) == 1; " +
                            "local reserved = tonumber(ARGV[4]); " +
                            "if vipLevel == 0 and vipSkilled and tonumber(score) >= math.max(0, maxLoad - reserved) then return nil; end; " +
                            "local queueScore = redis.call('ZSCORE', KEYS[2], first[1]); " +
                            "if not queueScore or redis.call('ZREM', KEYS[2], first[1]) == 0 then return nil; end; " +
                            "local enqueuedAt = redis.call('ZSCORE', KEYS[5], first[1]) or ARGV[3]; " +
                            "redis.call('ZINCRBY', KEYS[1], 1, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[8], ARGV[3], ARGV[1]); " +
                            "redis.call('ZADD', KEYS[3], ARGV[3], first[1]); " +
                            "redis.call('HSET', KEYS[4], first[1], ARGV[1] .. '|' .. queueScore .. '|' .. enqueuedAt); " +
                            "return first[1];",
                    String.class
            );

    /**
     * 分配失败时原子恢复原排队位置、客服负载和待确认分配记录。
     */
    static final DefaultRedisScript<Long>
            ROLLBACK_ASSIGNMENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local payload = redis.call('HGET', KEYS[4], ARGV[1]); " +
                            "if not payload then return 0; end; " +
                            "local split = string.find(payload, '|', 1, true); " +
                            "if not split then return 0; end; " +
                            "local secondSplit = string.find(payload, '|', split + 1, true); " +
                            "if not secondSplit then return 0; end; " +
                            "local storedAgent = string.sub(payload, 1, split - 1); " +
                            "local queueScore = tonumber(string.sub(payload, split + 1, secondSplit - 1)); " +
                            "local enqueuedAt = tonumber(string.sub(payload, secondSplit + 1)); " +
                            "if storedAgent ~= ARGV[2] then return 0; end; " +
                            "if ARGV[3] == '1' then " +
                            "redis.call('ZADD', KEYS[2], queueScore, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[5], enqueuedAt, ARGV[1]); " +
                            "else redis.call('ZREM', KEYS[6], ARGV[1]); end; " +
                            "local load = redis.call('ZSCORE', KEYS[1], ARGV[2]); " +
                            "if load then redis.call('ZADD', KEYS[1], math.max(0, tonumber(load) - 1), ARGV[2]); end; " +
                            "redis.call('ZREM', KEYS[3], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[4], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    static final DefaultRedisScript<Long>
            CLEAR_ASSIGNMENT_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[1], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[2], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    /**
     * MySQL会话创建成功后，原子提交全部Redis会话索引，
     * 并在同一次脚本中清除待确认分配记录。
     */
    static final DefaultRedisScript<Long>
            COMMIT_ACTIVE_SESSION_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1]); " +
                            "redis.call('SET', KEYS[2], ARGV[2]); " +
                            "redis.call('SET', KEYS[3], ARGV[3]); " +
                            "redis.call('SADD', KEYS[4], ARGV[1]); " +
                            "redis.call('HSET', KEYS[5], " +
                            "'status', ARGV[4], " +
                            "'createTime', ARGV[5], " +
                            "'vipLevel', ARGV[6], " +
                            "'agentId', ARGV[3]); " +
                            "redis.call('HDEL', KEYS[5], 'endTime'); " +
                            "redis.call('ZREM', KEYS[6], ARGV[2]); " +
                            "redis.call('HDEL', KEYS[7], ARGV[2]); " +
                            "redis.call('ZREM', KEYS[8], ARGV[2]); " +
                            "redis.call('ZREM', KEYS[10], ARGV[2]); " +
                            "redis.call('ZADD', KEYS[9], 'NX', ARGV[7], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    /**
     * 同一VIP等级内按入队时间保持FIFO；VIP等级越高，score越小。
     * 真实入队时间单独写入QUEUE_ENQUEUED_AT，供超时清扫和等待时长统计使用。
     */
    static final DefaultRedisScript<Long>
            ENQUEUE_WAITING_USER_SCRIPT =
            new DefaultRedisScript<>(
                    "local now = tonumber(ARGV[2]); " +
                            "local vipLevel = tonumber(ARGV[3]); " +
                            "local offset = tonumber(ARGV[4]); " +
                            "local last = tonumber(redis.call('HGET', KEYS[2], ARGV[3]) or '0'); " +
                            "local score = now - vipLevel * offset; " +
                            "if last >= score then score = last + 0.001; end; " +
                            "redis.call('HSET', KEYS[2], ARGV[3], tostring(score)); " +
                            "redis.call('ZADD', KEYS[1], score, ARGV[1]); " +
                            "redis.call('ZADD', KEYS[3], now, ARGV[1]); " +
                            "redis.call('HSET', KEYS[4], ARGV[1], ARGV[3]); " +
                            "if vipLevel == 0 then redis.call('ZADD', KEYS[5], now + tonumber(ARGV[5]), ARGV[1]); else redis.call('ZREM', KEYS[5], ARGV[1]); end; " +
                            "return 1;",
                    Long.class
            );

    /** 在 Redis 中原子完成会话索引、状态和客服负载的收敛。 */
    static final DefaultRedisScript<Long>
            FINALIZE_SESSION_REDIS_SCRIPT =
            new DefaultRedisScript<>(
                    "local alreadyClosed = redis.call('HGET', KEYS[4], 'status') == 'CLOSED'; " +
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]); end; " +
                            "redis.call('DEL', KEYS[2]); redis.call('DEL', KEYS[3]); " +
                            "redis.call('SREM', KEYS[5], ARGV[1]); " +
                            "redis.call('HSET', KEYS[4], 'status', 'CLOSED', 'endTime', ARGV[2]); " +
                            "redis.call('EXPIRE', KEYS[4], ARGV[3]); " +
                            "if not alreadyClosed then " +
                            "if ARGV[5] == '1' then redis.call('ZREM', KEYS[6], ARGV[4]); " +
                            "else local score = redis.call('ZSCORE', KEYS[6], ARGV[4]); " +
                            "if score then redis.call('ZADD', KEYS[6], math.max(0, tonumber(score) - 1), ARGV[4]); end; end; end; " +
                            "redis.call('ZREM', KEYS[7], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[8], ARGV[1]); " +
                            "redis.call('ZREM', KEYS[9], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

    static final DefaultRedisScript<Long>
            MARK_SESSION_FINALIZE_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]); " +
                            "redis.call('HSET', KEYS[2], ARGV[1], ARGV[3]); " +
                            "return 1;",
                    Long.class
            );

    static final DefaultRedisScript<Long>
            CLEAR_SESSION_FINALIZE_PENDING_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('ZREM', KEYS[1], ARGV[1]); " +
                            "redis.call('HDEL', KEYS[2], ARGV[1]); " +
                            "return 1;",
                    Long.class
            );

}
