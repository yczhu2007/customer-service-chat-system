package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.repository.ChatRedisRepository;

import java.util.concurrent.TimeUnit;

/** Maintains WebSocket mappings, online hashes, TTL and heartbeat indexes. */
final class ChatPresenceStateStore {

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
                        + RedisConstants
                        .HEARTBEAT_TIMEOUT_MILLIS;


        String userOnlineKey =
                RedisConstants.USER_ONLINE
                        + userId;


        String userWsKey =
                RedisConstants.USER_WS
                        + userId;


        String wsSessionKey =
                RedisConstants.WS_SESSION
                        + wsSessionId;
        /*
         * 查询用户之前保存的WebSocket连接。
         */
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


        /*
         * userId → wsSessionId
         */
        chatRedisRepository.setValue(
                        userWsKey,
                        wsSessionId,
                        RedisConstants
                                .ONLINE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );


        /*
         * wsSessionId → userId
         */
        chatRedisRepository.setValue(
                        wsSessionKey,
                        userId,
                        RedisConstants
                                .ONLINE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );


        /*
         * Hash也必须单独设置TTL。
         */
        chatRedisRepository.expire(
                userOnlineKey,
                RedisConstants
                        .ONLINE_TTL_SECONDS,
                TimeUnit.SECONDS
        );


        /*
         * 记录全局在线用户。
         */
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

        chatRedisRepository.delete(
                RedisConstants.USER_ONLINE
                        + userId
        );


        chatRedisRepository.delete(
                RedisConstants.USER_WS
                        + userId
        );


        if (
                wsSessionId != null &&
                        !wsSessionId.isBlank()
        ) {

            chatRedisRepository.delete(
                    RedisConstants.WS_SESSION
                            + wsSessionId
            );
        }


        chatRedisRepository.setRemove(
                        RedisConstants.ONLINE_USERS,
                        userId
                );


        chatRedisRepository.sortedSetRemove(
                        RedisConstants.ONLINE_HEARTBEAT,
                        userId
                );
    }
}
