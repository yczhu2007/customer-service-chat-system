package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.repository.ChatRedisRepository;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 维护 WebSocket 映射、在线状态、TTL 和心跳索引。 */
final class ChatPresenceStateStore {

    private static final DefaultRedisScript<Long> CLEANUP_ONLINE_STATE_SCRIPT =
            new DefaultRedisScript<>(
                    "local mappedWs = redis.call('GET', KEYS[2]); "
                            + "if not mappedWs or mappedWs ~= ARGV[1] then return 0; end; "
                            + "local mappedUser = redis.call('GET', KEYS[3]); "
                            + "if mappedUser == ARGV[2] then redis.call('DEL', KEYS[3]); end; "
                            + "redis.call('DEL', KEYS[1], KEYS[2]); "
                            + "redis.call('SREM', KEYS[4], ARGV[2]); "
                            + "redis.call('ZREM', KEYS[5], ARGV[2]); "
                            + "return 1;",
                    Long.class
            );

    private final ChatRedisRepository chatRedisRepository;

    ChatPresenceStateStore(ChatRedisRepository chatRedisRepository) {
        this.chatRedisRepository = chatRedisRepository;
    }

    void refreshOnlineState(
            String userId,
            String wsSessionId,
            int vipLevel
    ) {

        long nowMillis =
                System.currentTimeMillis();


        long timeoutAt =
                nowMillis
                        + RedisConstants.HEARTBEAT_TIMEOUT_SECONDS * 1000L;


        String userOnlineKey =
                RedisConstants.USER_ONLINE
                        + userId;


        String userWsKey =
                RedisConstants.USER_WS
                        + userId;


        String wsSessionKey =
                RedisConstants.WS_SESSION
                        + wsSessionId;
        String previousWsSessionId =
                chatRedisRepository.getValue(
                                userWsKey
                        );


        /*
         * 用户建立了新连接时，
         * 删除旧连接的反向映射。
         *
         * 防止旧连接以后触发断开事件，
         * 误结束新连接对应的聊天会话。
         */
        if (
                previousWsSessionId != null &&
                        !previousWsSessionId.isBlank() &&
                        !wsSessionId.equals(
                                previousWsSessionId
                        )
        ) {

            chatRedisRepository.delete(
                    RedisConstants.WS_SESSION
                            + previousWsSessionId
            );
        }
        chatRedisRepository.hashPut(
                        userOnlineKey,
                        "lastHeartbeat",
                        String.valueOf(
                                nowMillis
                        )
                );


        chatRedisRepository.hashPut(
                        userOnlineKey,
                        "wsSessionId",
                        wsSessionId
                );

        chatRedisRepository.hashPut(
                        userOnlineKey,
                        "vipLevel",
                        String.valueOf(vipLevel)
                );


        chatRedisRepository.setValue(
                        userWsKey,
                        wsSessionId,
                        RedisConstants
                                .ONLINE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );


        chatRedisRepository.setValue(
                        wsSessionKey,
                        userId,
                        RedisConstants
                                .ONLINE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );


        chatRedisRepository.expire(
                userOnlineKey,
                RedisConstants
                        .ONLINE_TTL_SECONDS,
                TimeUnit.SECONDS
        );


        chatRedisRepository.setAdd(
                        RedisConstants.ONLINE_USERS,
                        userId
                );


        /*
         * 当前定时器查询score <= 当前时间，
         * 所以这里保存的是超时截止时间。
         */
        chatRedisRepository.sortedSetAdd(
                        RedisConstants.ONLINE_HEARTBEAT,
                        userId,
                        timeoutAt
                );
    }

    void cleanupOnlineState(
            String userId,
            String wsSessionId
    ) {
        String effectiveWsSessionId = wsSessionId;
        if (effectiveWsSessionId == null || effectiveWsSessionId.isBlank()) {
            effectiveWsSessionId = chatRedisRepository.getValue(
                    RedisConstants.USER_WS + userId
            );
        }
        if (effectiveWsSessionId == null || effectiveWsSessionId.isBlank()) {
            return;
        }

        chatRedisRepository.execute(
                CLEANUP_ONLINE_STATE_SCRIPT,
                List.of(
                        RedisConstants.USER_ONLINE + userId,
                        RedisConstants.USER_WS + userId,
                        RedisConstants.WS_SESSION + effectiveWsSessionId,
                        RedisConstants.ONLINE_USERS,
                        RedisConstants.ONLINE_HEARTBEAT
                ),
                effectiveWsSessionId,
                userId
        );
    }
}
