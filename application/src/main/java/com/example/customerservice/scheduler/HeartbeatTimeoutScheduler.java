package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.ChatPresenceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 定时扫描心跳超时用户
 */
@Component
@Slf4j
public class HeartbeatTimeoutScheduler {

    private final StringRedisTemplate redisTemplate;

    private final ChatPresenceOperations chatPresenceOperations;


    public HeartbeatTimeoutScheduler(
            StringRedisTemplate redisTemplate,
            ChatPresenceOperations chatPresenceOperations
    ) {

        this.redisTemplate =
                redisTemplate;

        this.chatPresenceOperations =
                chatPresenceOperations;
    }


    /**
     * 每10秒执行一次。
     * fixedDelay表示：
     * 上一次任务结束10秒后，
     * 再执行下一次任务。
     */
    @Scheduled(
            fixedDelay = 10_000
    )
    public void scanHeartbeatTimeout() {

        long nowMillis =
                System.currentTimeMillis();


        /*
         * 查找score小于等于当前时间的用户。
         *
         * 每次最多处理100个，
         * 避免一次处理过多数据。
         */
        Set<String> timeoutUserIds =
                redisTemplate
                        .opsForZSet()
                        .rangeByScore(
                                RedisConstants
                                        .ONLINE_HEARTBEAT,
                                0,
                                nowMillis,
                                0,
                                100
                        );


        if (
                timeoutUserIds == null ||
                        timeoutUserIds.isEmpty()
        ) {

            return;
        }


        for (
                String userId :
                timeoutUserIds
        ) {

            try {

                chatPresenceOperations
                        .handleHeartbeatTimeout(
                                userId
                        );

            } catch (Exception e) {

                log.info(
                        "处理心跳超时失败，用户："
                                + userId
                                + "，原因："
                                + e.getMessage()
                );
            }
        }
    }
}
