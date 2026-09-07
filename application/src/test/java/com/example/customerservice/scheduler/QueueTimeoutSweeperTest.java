package com.example.customerservice.scheduler;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.service.ChatRoutingOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueTimeoutSweeperTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatRoutingOperations chatRoutingOperations;
    @Mock private DistributedSchedulerLock schedulerLock;

    private QueueTimeoutSweeper sweeper;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(schedulerLock).execute(anyString(), any(Runnable.class));
        sweeper = new QueueTimeoutSweeper(
                redisTemplate, messagingTemplate, chatRoutingOperations,
                schedulerLock, 300, 120
        );
    }

    @Test
    void backfillsMissingQueueTimestampsInOneBatch() {
        when(zSetOperations.range(RedisConstants.QUEUE_PENDING, 0, 99))
                .thenReturn(new LinkedHashSet<>(Set.of("U001", "U002")));
        when(zSetOperations.score(RedisConstants.QUEUE_ENQUEUED_AT, "U001"))
                .thenReturn(null);
        when(zSetOperations.score(RedisConstants.QUEUE_ENQUEUED_AT, "U002"))
                .thenReturn(null);
        when(zSetOperations.rangeByScore(
                eq(RedisConstants.QUEUE_ENQUEUED_AT), eq(0D), anyDouble(), eq(0L), eq(100L)
        )).thenReturn(Set.of());

        sweeper.removeTimedOutUsers();

        ArgumentCaptor<Iterable<String>> users = ArgumentCaptor.forClass(Iterable.class);
        verify(chatRoutingOperations).backfillWaitingUsers(users.capture());
        assertEquals(Set.of("U001", "U002"),
                java.util.stream.StreamSupport.stream(users.getValue().spliterator(), false)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void notificationFailureDoesNotStopLaterTimeoutCleanup() {
        when(zSetOperations.range(RedisConstants.QUEUE_PENDING, 0, 99))
                .thenReturn(Set.of());
        when(zSetOperations.rangeByScore(
                eq(RedisConstants.QUEUE_ENQUEUED_AT), eq(0D), anyDouble(), eq(0L), eq(100L)
        )).thenReturn(new LinkedHashSet<>(Set.of("U001", "U002")));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        doAnswer(invocation -> {
            if ("U001".equals(invocation.getArgument(0))) {
                throw new RuntimeException("broker unavailable");
            }
            return null;
        })
                .when(messagingTemplate)
                .convertAndSendToUser(anyString(), eq("/queue/chat"), any());

        assertDoesNotThrow(sweeper::removeTimedOutUsers);

        verify(messagingTemplate).convertAndSendToUser(eq("U002"), eq("/queue/chat"), any());
        verify(chatRoutingOperations).refreshWaitingPositions();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void vipTimeoutsReuseTheOnlineAgentListWithinOneSweep() {
        when(zSetOperations.range(RedisConstants.QUEUE_PENDING, 0, 99))
                .thenReturn(Set.of());
        when(zSetOperations.rangeByScore(
                eq(RedisConstants.QUEUE_ENQUEUED_AT), eq(0D), anyDouble(), eq(0L), eq(100L)
        )).thenReturn(new LinkedHashSet<>(Set.of("U001", "U002")));
        when(zSetOperations.range(RedisConstants.AGENT_LOAD, 0, -1))
                .thenReturn(Set.of("A001"));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(2L);

        sweeper.removeTimedOutUsers();

        verify(zSetOperations).range(RedisConstants.AGENT_LOAD, 0, -1);
    }
}
