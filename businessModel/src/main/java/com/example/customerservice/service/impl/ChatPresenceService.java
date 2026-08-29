package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.ChatSessionDTO;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatPresenceCallbacks;
import com.example.customerservice.service.ChatPresenceOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class ChatPresenceService implements ChatPresenceOperations {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageReadMapper chatMessageReadMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final ObjectProvider<ChatPresenceCallbacks> callbacksProvider;
    private final long agentReconnectGraceMillis;
    private final ChatPresenceStateStore presenceStateStore;

    public ChatPresenceService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            SysUserRoleMapper sysUserRoleMapper,
            ObjectProvider<ChatPresenceCallbacks> callbacksProvider,
            long agentReconnectGraceSeconds
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageReadMapper = chatMessageReadMapper;
        this.messagingTemplate = messagingTemplate;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.callbacksProvider = callbacksProvider;
        this.agentReconnectGraceMillis = agentReconnectGraceSeconds * 1000L;
        this.presenceStateStore = new ChatPresenceStateStore(chatRedisRepository);
    }

    private ChatPresenceCallbacks callbacks() {
        return callbacksProvider.getObject();
    }
    @Override
    @Transactional
    public void handleDisconnect(
            String wsSessionId
    ) {

        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            return;
        }
        String userId =
                chatRedisRepository.getValue(
                                RedisConstants.WS_SESSION
                                        + wsSessionId
                        );


        if (
                userId == null ||
                        userId.isBlank()
        ) {

            /*
             * 映射已经不存在，
             * 说明该连接可能已经被清理。
             */
            return;
        }
        String presenceLockToken = acquirePresenceLock(userId);
        if (presenceLockToken == null) {
            return;
        }
        ScheduledFuture<?> presenceLockRenewal = startPresenceLockRenewal(userId, presenceLockToken);
        try {
        String currentWsSessionId =
                chatRedisRepository.getValue(
                                RedisConstants.USER_WS
                                        + userId
                        );


        /*
         * 当前用户已经建立了更新的连接。
         *
         * 旧连接断开时，只删除旧连接的反向映射，
         * 不能清理用户当前在线状态，也不能结束聊天会话。
         */
        if (
                currentWsSessionId != null &&
                        !wsSessionId.equals(
                                currentWsSessionId
                        )
        ) {

            chatRedisRepository.delete(
                    RedisConstants.WS_SESSION
                            + wsSessionId
            );


            log.info(
                    "忽略旧WebSocket连接断开，用户："
                            + userId
                            + "，旧连接："
                            + wsSessionId
                            + "，当前连接："
                            + currentWsSessionId
            );


            return;
        }


        /*
         * 当前连接真正断开，
         * 执行完整业务清理。
         */
        handleOffline(
                userId,
                wsSessionId,
                ChatConstants.REASON_WEBSOCKET_DISCONNECT
        );
        } finally {
            chatRedisRepository.stopLockRenewal(presenceLockRenewal);
            releasePresenceLock(userId, presenceLockToken);
        }
    }
    private ScheduledFuture<?> startPresenceLockRenewal(String userId, String token) {
        return chatRedisRepository.startLockRenewal(
                RedisConstants.PRESENCE_OPERATION_LOCK + userId,
                token,
                RedisConstants.PRESENCE_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * 处理聊天消息
     */
    @Override
    public void handleHeartbeat(
            String userId,
            String wsSessionId
    ) {
        if (
                userId == null ||
                        userId.isBlank() ||
                        wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            return;
        }
        String presenceLockToken = acquirePresenceLock(userId);
        if (presenceLockToken == null) {
            return;
        }
        ScheduledFuture<?> presenceLockRenewal = startPresenceLockRenewal(userId, presenceLockToken);
        try {
        String currentWsSessionId =
                chatRedisRepository.getValue(
                                RedisConstants.USER_WS
                                        + userId
                        );


        if (
                !wsSessionId.equals(
                        currentWsSessionId
                )
        ) {

            log.info(
                    "忽略旧WebSocket连接的心跳，用户："
                            + userId
                            + "，wsSessionId："
                            + wsSessionId
            );

            return;
        }
        refreshOnlineState(
                userId,
                wsSessionId
        );
        } finally {
            chatRedisRepository.stopLockRenewal(presenceLockRenewal);
            releasePresenceLock(userId, presenceLockToken);
        }
    }


    @Override
    @Transactional
    public void handleHeartbeatTimeout(
            String userId
    ) {

        if (
                userId == null ||
                        userId.isBlank()
        ) {

            return;
        }
        String presenceLockToken = acquirePresenceLock(userId);
        if (presenceLockToken == null) {
            return;
        }
        ScheduledFuture<?> presenceLockRenewal = startPresenceLockRenewal(userId, presenceLockToken);
        try {


        /*
         * 定时任务扫描到用户后，
         * 用户可能已经发送了新的心跳。
         * 必须在执行清理前读取最新超时时间。
         *
         * 本方法与handleHeartbeat使用同一Redis分布式锁，
         * 避免多实例下心跳刷新与超时清理并发执行。
         */
        Double timeoutAt =
                chatRedisRepository.sortedSetScore(
                                RedisConstants
                                        .ONLINE_HEARTBEAT,
                                userId
                        );


        if (
                timeoutAt == null ||
                        timeoutAt
                                > System.currentTimeMillis()
        ) {

            return;
        }


        String wsSessionId =
                chatRedisRepository.getValue(
                                RedisConstants.USER_WS
                                        + userId
                        );


        handleOffline(
                userId,
                wsSessionId,
                ChatConstants.REASON_HEARTBEAT_TIMEOUT
        );
        } finally {
            chatRedisRepository.stopLockRenewal(presenceLockRenewal);
            releasePresenceLock(userId, presenceLockToken);
        }
    }

    private String acquirePresenceLock(String userId) {
        return chatRedisRepository.acquireLock(
                RedisConstants.PRESENCE_OPERATION_LOCK + userId,
                RedisConstants.PRESENCE_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void releasePresenceLock(String userId, String token) {
        chatRedisRepository.releaseLock(
                RedisConstants.PRESENCE_OPERATION_LOCK + userId,
                token
        );
    }
    /**
     * WebSocket断开和心跳超时的统一处理入口。
     */
    private void handleOffline(
            String userId,
            String wsSessionId,
            String reason
    ) {

        /*
         * 必须先判断角色。
         * 清理agent:load以后就无法通过ZSET判断角色了。
         */
        Set<String> roleCodes =
                sysUserRoleMapper
                        .findRoleCodesByUserId(
                                userId
                        );


        boolean isAgent = chatRedisRepository.sortedSetScore(
                RedisConstants.AGENT_LOAD,
                userId
        ) != null;
        /* Stop new assignments before any session cleanup or reconnect grace handling. */
        if (isAgent) {
            chatRedisRepository.sortedSetRemove(
                    RedisConstants.AGENT_LOAD,
                    userId
            );
            chatRedisRepository.sortedSetRemove(
                    RedisConstants.AGENT_LAST_ASSIGNED,
                    userId
            );
        }
        cleanupOnlineState(
                userId,
                wsSessionId
        );

        if (isAgent && (ChatConstants.REASON_WEBSOCKET_DISCONNECT.equals(reason)
                || ChatConstants.REASON_HEARTBEAT_TIMEOUT.equals(reason))) {
            scheduleAgentReconnectGrace(userId);
            return;
        }


        /*
         * 如果是仍在排队的普通用户，
         * 断线后必须从等待队列删除。
         */
        chatRedisRepository.sortedSetRemove(
                        RedisConstants.QUEUE_PENDING,
                        userId
                );
        chatRedisRepository.sortedSetRemove(
                        RedisConstants.QUEUE_ENQUEUED_AT,
                        userId
                );
        chatRedisRepository.hashDelete(
                        RedisConstants.QUEUE_VIP_LEVEL,
                        userId
                );
        chatRedisRepository.sortedSetRemove(
                        RedisConstants.VIP_CALLBACK_PENDING,
                        userId
                );
        if (isAgent) {

        disconnectAgent(
                    userId,
                    reason
            );

        } else {

            handleUserDisconnect(
                    userId,
                    reason
            );
        }
    }

    private void handleUserDisconnect(
            String userId,
            String reason
    ) {

        String sessionId =
                chatRedisRepository.getValue(
                                RedisConstants
                                        .USER_ACTIVE_SESSION
                                        + userId
                        );


        if (
                sessionId == null ||
                        sessionId.isBlank()
        ) {

            return;
        }


        ChatSession session =
                chatSessionMapper.selectById(
                        sessionId
                );


        if (session == null) {

            /*
             * 数据库已经没有会话时，
             * 至少删除残留的活动映射。
             */
            chatRedisRepository.delete(
                    RedisConstants
                            .USER_ACTIVE_SESSION
                            + userId
            );

            return;
        }


        boolean finalized =
                callbacks().finalizePresenceSession(
                        session,
                        null
                );


        if (!finalized) {

            return;
        }


        /*
         * 普通用户断开，但客服仍在线，
         * 所以只将客服负载减1。
         */
        callbacks().publishSessionClosed(

                session.getAgentId(),

                session.getId(),

                reason
        );


        /*
         * 当前客服释放了容量，
         * 尝试接待等待队列中的下一位用户。
         */
        callbacks().reassignDisconnectedUser(
                session.getAgentId()
        );
    }

    @Override
    public void disconnectAgent(
            String agentId,
            String reason
    ) {
        /*
         * 优先通过 Redis 反向索引获取该客服的活动会话。
         * 缓存索引只保存 sessionId，仍需根据 ID 从 MySQL 取得
         * 会话实体，以便执行会话最终结算和持久化状态更新。
         */
        Set<String> indexedSessionIds = chatRedisRepository.setMembers(
                RedisConstants.agentSessionsKey(agentId)
        );

        List<ChatSession> activeSessions = new ArrayList<>();

        if (indexedSessionIds != null && !indexedSessionIds.isEmpty()) {
            for (String sessionId : indexedSessionIds) {
                ChatSession session = chatSessionMapper.selectById(sessionId);
                if (session != null && ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
                    activeSessions.add(session);
                } else {
                    chatRedisRepository.setRemove(
                            RedisConstants.agentSessionsKey(agentId),
                            sessionId
                    );
                }
            }

            activeSessions.sort(
                    Comparator.comparing(ChatSession::getCreateTime)
            );
        }

        /*
         * Redis 索引不存在、丢失或全为失效数据时，才查询 MySQL 兜底，
         * 并在下方重新补齐 Redis 反向索引。
         */
        if (activeSessions.isEmpty()) {
            activeSessions = chatSessionMapper.selectList(
                    Wrappers.<ChatSession>lambdaQuery()
                            .eq(ChatSession::getAgentId, agentId)
                            .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_ACTIVE)
                            .orderByAsc(ChatSession::getCreateTime)
            );

            for (ChatSession session : activeSessions) {
                chatRedisRepository.setAdd(
                        RedisConstants.agentSessionsKey(agentId),
                        session.getId()
                );
            }
        }

        for (ChatSession session : activeSessions) {
            if (!callbacks().finalizePresenceSession(session, agentId)) {
                continue;
            }

            String userId =
                    session.getUserId();

            ChatSessionDTO notice =
                    ChatSessionDTO.fromEntity(
                            session,
                            ChatConstants.EVENT_SESSION_CLOSED
                    );
            notice.setReason(
                    reason
            );
            notice.setStatus(
                    ChatConstants.SESSION_STATUS_CLOSED
            );

            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/chat",
                    notice
            );

            /*
             * 手动离线不会关闭客服的WebSocket连接，因此也要立即通知
             * 当前客服清空已结束的会话，避免客服继续在失效会话中操作。
             */
            messagingTemplate.convertAndSendToUser(
                    agentId,
                    "/queue/chat",
                    notice
            );

            /*
             * 用户仍在线时重新进入分配流程。
             */
            Boolean userOnline =
                    chatRedisRepository.hasKey(
                            RedisConstants.USER_WS
                                    + userId
                    );

            if (Boolean.TRUE.equals(userOnline)) {
                callbacks().reconnectUser(
                        userId
                );
            }
        }
        chatRedisRepository.sortedSetRemove(
                RedisConstants.AGENT_LOAD,
                agentId
        );
        chatRedisRepository.sortedSetRemove(
                RedisConstants.AGENT_LAST_ASSIGNED,
                agentId
        );
    }

    @Override
    public void registerOnline(
            String userId,
            String wsSessionId
    ) {

        refreshOnlineState(
                userId,
                wsSessionId
        );

    }

    @Override
    public void handleAgentReconnectGraceTimeout(String agentId) {
        Double deadline = chatRedisRepository.sortedSetScore(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId
        );
        if (deadline == null || deadline > System.currentTimeMillis()) {
            return;
        }
        chatRedisRepository.sortedSetRemove(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId
        );
        handleOffline(agentId, null, "AGENT_RECONNECT_TIMEOUT");
    }

    private void scheduleAgentReconnectGrace(String agentId) {
        chatRedisRepository.sortedSetAdd(
                RedisConstants.AGENT_RECONNECT_GRACE,
                agentId,
                System.currentTimeMillis() + agentReconnectGraceMillis
        );
    }




    private void refreshOnlineState(
            String userId,
            String wsSessionId
    ) {
        presenceStateStore.refreshOnlineState(
                userId,
                wsSessionId,
                callbacks().resolveVipLevel(userId)
        );
    }
    /**
     * 清理用户或客服的在线状态。
     */
    private void cleanupOnlineState(
            String userId,
            String wsSessionId
    ) {
        presenceStateStore.cleanupOnlineState(userId, wsSessionId);
    }

}
