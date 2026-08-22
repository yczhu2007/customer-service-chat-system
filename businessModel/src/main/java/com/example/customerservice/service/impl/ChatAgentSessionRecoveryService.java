package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客服上线时的活动会话恢复。
 *
 * <p>Redis反向索引按批次使用IN查询一次恢复，避免逐个会话查询数据库；索引缺失时再按
 * 客服和活动状态查询MySQL。分批查询限制单条SQL参数量，适用于大量客服同时重连时的
 * 恢复场景。</p>
 */
@Slf4j
public class ChatAgentSessionRecoveryService {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final int queryBatchSize;

    public ChatAgentSessionRecoveryService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            int queryBatchSize
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.queryBatchSize = Math.max(10, Math.min(queryBatchSize, 500));
    }

    public List<ChatSession> restoreActiveSessions(String agentId) {
        String indexKey = RedisConstants.agentSessionsKey(agentId);
        Set<String> indexedSessionIds = chatRedisRepository.setMembers(indexKey);
        List<ChatSession> activeSessions = queryIndexedSessions(
                agentId,
                indexedSessionIds
        );

        if (activeSessions.isEmpty()) {
            activeSessions = chatSessionMapper.selectList(
                    Wrappers.<ChatSession>lambdaQuery()
                            .eq(ChatSession::getAgentId, agentId)
                            .eq(
                                    ChatSession::getStatus,
                                    ChatConstants.SESSION_STATUS_ACTIVE
                            )
                            .orderByAsc(ChatSession::getCreateTime)
                            .orderByAsc(ChatSession::getId)
            );
        }

        rebuildAgentSessionIndex(indexKey, activeSessions);
        return activeSessions;
    }

    private List<ChatSession> queryIndexedSessions(
            String agentId,
            Set<String> indexedSessionIds
    ) {
        if (indexedSessionIds == null || indexedSessionIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> ids = new ArrayList<>(indexedSessionIds);
        List<ChatSession> activeSessions = new ArrayList<>();
        Set<String> validSessionIds = new HashSet<>();

        for (int start = 0; start < ids.size(); start += queryBatchSize) {
            int end = Math.min(start + queryBatchSize, ids.size());
            List<String> batchIds = ids.subList(start, end);
            List<ChatSession> batch = chatSessionMapper.selectList(
                    Wrappers.<ChatSession>lambdaQuery()
                            .in(ChatSession::getId, batchIds)
                            .eq(ChatSession::getAgentId, agentId)
                            .eq(
                                    ChatSession::getStatus,
                                    ChatConstants.SESSION_STATUS_ACTIVE
                            )
            );
            activeSessions.addAll(batch);
            batch.forEach(session -> validSessionIds.add(session.getId()));
        }

        List<String> staleSessionIds = ids.stream()
                .filter(sessionId -> !validSessionIds.contains(sessionId))
                .toList();
        if (!staleSessionIds.isEmpty()) {
            chatRedisRepository.setRemove(indexKeyFor(agentId), staleSessionIds.toArray());
            log.info(
                    "批量清理客服失效会话索引，agentId={}，count={}",
                    agentId,
                    staleSessionIds.size()
            );
        }

        activeSessions.sort(
                Comparator.comparing(
                                ChatSession::getCreateTime,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                ChatSession::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
        );
        return activeSessions;
    }

    private void rebuildAgentSessionIndex(
            String indexKey,
            List<ChatSession> activeSessions
    ) {
        if (activeSessions.isEmpty()) {
            return;
        }
        String[] sessionIds = activeSessions.stream()
                .map(ChatSession::getId)
                .toArray(String[]::new);
        chatRedisRepository.setAdd(indexKey, sessionIds);
    }

    private String indexKeyFor(String agentId) {
        return RedisConstants.agentSessionsKey(agentId);
    }
}
