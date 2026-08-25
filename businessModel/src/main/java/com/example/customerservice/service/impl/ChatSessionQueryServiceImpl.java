package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatSessionRating;
import com.example.customerservice.domain.ChatSessionTag;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.*;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionRatingMapper;
import com.example.customerservice.mapper.ChatSessionTagMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatSessionQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ChatSessionQueryServiceImpl implements ChatSessionQueryService {

    private final ChatSessionMapper sessionMapper;
    private final ChatSessionRatingMapper ratingMapper;
    private final SysUserMapper userMapper;
    private final ChatMessageReadMapper messageReadMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionTagMapper tagMapper;
    private final long averageHandleSeconds;

    public ChatSessionQueryServiceImpl(ChatSessionMapper sessionMapper,
                                       ChatSessionRatingMapper ratingMapper,
                                       SysUserMapper userMapper,
                                       ChatMessageReadMapper messageReadMapper,
                                       ChatRedisRepository chatRedisRepository,
                                       long averageHandleSeconds) {
        this(sessionMapper, ratingMapper, userMapper, messageReadMapper,
                chatRedisRepository, null, null, averageHandleSeconds);
    }

    public ChatSessionQueryServiceImpl(ChatSessionMapper sessionMapper,
                                       ChatSessionRatingMapper ratingMapper,
                                       SysUserMapper userMapper,
                                       ChatMessageReadMapper messageReadMapper,
                                       ChatRedisRepository chatRedisRepository,
                                       ChatSessionTagMapper tagMapper,
                                       long averageHandleSeconds) {
        this(sessionMapper, ratingMapper, userMapper, messageReadMapper,
                chatRedisRepository, tagMapper, null, averageHandleSeconds);
    }

    @Autowired
    public ChatSessionQueryServiceImpl(ChatSessionMapper sessionMapper,
                                       ChatSessionRatingMapper ratingMapper,
                                       SysUserMapper userMapper,
                                       ChatMessageReadMapper messageReadMapper,
                                       ChatRedisRepository chatRedisRepository,
                                       ChatSessionTagMapper tagMapper,
                                       ChatMessageMapper messageMapper,
                                       @Value("${app.chat.queue.average-handle-seconds:300}") long averageHandleSeconds) {
        this.sessionMapper = sessionMapper;
        this.ratingMapper = ratingMapper;
        this.userMapper = userMapper;
        this.messageReadMapper = messageReadMapper;
        this.messageMapper = messageMapper;
        this.chatRedisRepository = chatRedisRepository;
        this.tagMapper = tagMapper;
        this.averageHandleSeconds = Math.max(30L, averageHandleSeconds);
    }

    @Override
    @Transactional
    public SessionRatingVO rateSession(String userId, String sessionId, SessionRatingDTO request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!ChatConstants.SESSION_STATUS_CLOSED.equals(session.getStatus())) {
            throw new BusinessStateException("只能对已结束的会话进行评价");
        }
        if (!userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("只有会话发起方可以评价");
        }
        ChatSessionRating rating = new ChatSessionRating();
        rating.setSessionId(sessionId);
        rating.setUserId(userId);
        rating.setRating(request.getRating());
        rating.setComment(request.getComment());
        rating.setCreateTime(LocalDateTime.now());
        try {
            if (ratingMapper.insert(rating) != 1) {
                throw new IllegalStateException("评价保存失败");
            }
            return toRatingVO(rating);
        } catch (DuplicateKeyException e) {
            /* The primary-key constraint is the concurrency boundary. Replaying a
             * concurrent/already-completed request returns the persisted result. */
            ChatSessionRating existing = ratingMapper.selectById(sessionId);
            if (existing == null) {
                throw e;
            }
            return toRatingVO(existing);
        }
    }

    @Override
    public SessionRatingVO getSessionRating(String participantId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!participantId.equals(session.getUserId())
                && !participantId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("无权查看该会话评价");
        }
        ChatSessionRating rating = ratingMapper.selectById(sessionId);
        return rating == null ? null : toRatingVO(rating);
    }

    @Override
    public UserProfileSidebarVO getUserProfileSidebar(String agentId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!agentId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("只有会话分配的客服可以查看用户信息");
        }

        SysUser user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }

        Long totalSessions = sessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getUserId, session.getUserId())
        );

        ChatSession lastSession = sessionMapper.selectOne(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getUserId, session.getUserId())
                        .ne(ChatSession::getId, sessionId)
                        .orderByDesc(ChatSession::getCreateTime)
                        .last("LIMIT 1")
        );

        UserProfileSidebarVO vo = new UserProfileSidebarVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname());
        vo.setVipLevel(user.getVipLevel() == null ? 0 : user.getVipLevel());
        vo.setTotalSessionCount(totalSessions == null ? 0 : totalSessions.intValue());
        vo.setLastSessionTime(lastSession == null ? null : lastSession.getCreateTime());
        return vo;
    }

    @Override
    public ChatSessionMetadataVO getSessionMetadata(
            String actorId,
            boolean administrator,
            String sessionId
    ) {
        ChatSession session = requireSession(sessionId);
        if (!administrator
                && (actorId == null
                || actorId.isBlank()
                || (!Objects.equals(actorId, session.getUserId())
                && !Objects.equals(actorId, session.getAgentId())))) {
            throw new IllegalArgumentException("无权查看该会话元数据");
        }

        List<String> tags = requireTagMapper().selectBySessionId(sessionId).stream()
                .map(ChatSessionTag::getTag)
                .toList();
        return toMetadataVO(session, tags);
    }

    @Override
    @Transactional
    public ChatSessionMetadataVO updateSessionMetadata(
            String agentId,
            String sessionId,
            ChatSessionMetadataUpdateDTO request
    ) {
        ChatSession session = requireSession(sessionId);
        if (agentId == null
                || agentId.isBlank()
                || !Objects.equals(agentId, session.getAgentId())) {
            throw new IllegalArgumentException("只有会话分配的客服可以更新元数据");
        }
        return applySessionMetadata(session, request);
    }

    @Override
    @Transactional
    public ChatSessionMetadataVO updateSessionMetadataAsAdmin(
            String sessionId,
            ChatSessionMetadataUpdateDTO request
    ) {
        ChatSession session = requireSession(sessionId);
        return applySessionMetadata(session, request);
    }

    private ChatSessionMetadataVO applySessionMetadata(
            ChatSession session,
            ChatSessionMetadataUpdateDTO request
    ) {
        validateMetadataRequest(request);
        List<String> normalizedTags = normalizeTags(request.getTags());

        session.setTitle(request.getTitle());
        session.setPriority(request.getPriority());
        session.setCategory(request.getCategory());
        session.setMetadataUpdatedAt(LocalDateTime.now());
        if (sessionMapper.updateById(session) != 1) {
            throw new BusinessStateException("会话元数据更新失败");
        }

        ChatSessionTagMapper requiredTagMapper = requireTagMapper();
        requiredTagMapper.deleteBySessionId(session.getId());
        for (String value : normalizedTags) {
            ChatSessionTag tag = new ChatSessionTag();
            tag.setSessionId(session.getId());
            tag.setTag(value);
            if (requiredTagMapper.insert(tag) != 1) {
                throw new BusinessStateException("会话标签更新失败");
            }
        }
        return toMetadataVO(session, normalizedTags);
    }

    private ChatSessionTagMapper requireTagMapper() {
        if (tagMapper == null) {
            throw new IllegalStateException(
                    "Metadata operations require the ChatSessionTagMapper dependency"
            );
        }
        return tagMapper;
    }

    private ChatSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        return session;
    }

    private void validateMetadataRequest(ChatSessionMetadataUpdateDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("会话元数据不能为空");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (request.getTitle().length() > ChatConstants.SESSION_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("标题长度不能超过100个字符");
        }
        if (!ChatConstants.isValidSessionPriority(request.getPriority())) {
            throw new IllegalArgumentException("优先级不合法");
        }
        if (!ChatConstants.isValidSessionCategory(request.getCategory())) {
            throw new IllegalArgumentException("分类不合法");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : tags) {
            if (value == null) {
                throw new IllegalArgumentException("标签不能为空");
            }
            String tag = value.trim().toLowerCase(Locale.ROOT);
            if (tag.isBlank()) {
                throw new IllegalArgumentException("标签不能为空");
            }
            if (tag.length() > ChatConstants.SESSION_TAG_MAX_LENGTH) {
                throw new IllegalArgumentException("单个标签长度不能超过32个字符");
            }
            normalized.add(tag);
        }
        if (normalized.size() > ChatConstants.SESSION_TAG_MAX_COUNT) {
            throw new IllegalArgumentException("标签数量不能超过10个");
        }
        return List.copyOf(normalized);
    }

    private ChatSessionMetadataVO toMetadataVO(ChatSession session, List<String> tags) {
        ChatSessionMetadataVO vo = new ChatSessionMetadataVO();
        vo.setSessionId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setPriority(session.getPriority());
        vo.setCategory(session.getCategory());
        vo.setTags(tags);
        vo.setMetadataUpdatedAt(session.getMetadataUpdatedAt());
        return vo;
    }

    private SessionRatingVO toRatingVO(ChatSessionRating rating) {
        SessionRatingVO vo = new SessionRatingVO();
        vo.setSessionId(rating.getSessionId());
        vo.setRating(rating.getRating());
        vo.setComment(rating.getComment());
        vo.setCreateTime(rating.getCreateTime());
        return vo;
    }

    @Override
    public QueueStatusVO getQueueStatus(String userId) {
        QueueStatusVO vo = new QueueStatusVO();

        Long onlineAgentCount = chatRedisRepository.sortedSetCardinality(RedisConstants.AGENT_LOAD);
        vo.setOnlineAgentCount(onlineAgentCount == null ? 0 : onlineAgentCount);

        Long queueSize = chatRedisRepository.sortedSetCardinality(RedisConstants.QUEUE_PENDING);
        vo.setQueueSize(queueSize == null ? 0 : queueSize);

        if (userId != null && !userId.isBlank()) {
            Long rank = chatRedisRepository.sortedSetRank(RedisConstants.QUEUE_PENDING, userId);
            if (rank != null) {
                vo.setMyPosition(rank + 1);
                vo.setEstimatedWaitSeconds(estimateWaitSeconds(rank + 1, vo.getOnlineAgentCount()));
            }
        }

        return vo;
    }

    private Long estimateWaitSeconds(long position, long onlineAgentCount) {
        if (position <= 0 || onlineAgentCount <= 0) {
            return null;
        }
        long serviceRounds = (position + onlineAgentCount - 1) / onlineAgentCount;
        return serviceRounds * averageHandleSeconds;
    }

    // ── 归档状态筛选的会话列表 ──────────────────────────────────────────────

    @Override
    public PageResult<ChatSessionListItemVO> findMySessions(
            String participantId,
            SessionParticipantType participantType,
            String statusFilter,
            String archiveStatusFilter, long pageNo, long pageSize) {
        if (participantType == null) {
            throw new IllegalArgumentException("participantType不能为空");
        }
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));

        boolean filterArchive = archiveStatusFilter != null && !archiveStatusFilter.isBlank();
        boolean filterUnarchived = "NONE".equals(archiveStatusFilter);

        Page<ChatSession> page = sessionMapper.selectPage(
                new Page<>(normalizedPageNo, normalizedPageSize),
                buildSessionScopeQuery(
                        participantId,
                        participantType,
                        statusFilter,
                        archiveStatusFilter,
                        filterArchive,
                        filterUnarchived
                )
                        .orderByDesc(ChatSession::getCreateTime)
                        .orderByDesc(ChatSession::getId)
        );

        List<String> sessionIds = page.getRecords().stream()
                .map(ChatSession::getId)
                .toList();

        Map<String, Long> unreadCounts = loadUnreadCounts(sessionIds, participantId);
        Map<String, List<String>> tagsBySessionId = loadSessionTags(sessionIds);

        List<ChatSessionListItemVO> records = page.getRecords().stream()
                .map(s -> {
                    ChatSessionListItemVO vo = new ChatSessionListItemVO();
                    vo.setSessionId(s.getId());
                    vo.setUserId(s.getUserId());
                    vo.setAgentId(s.getAgentId());
                    vo.setStatus(s.getStatus());
                    vo.setCreateTime(s.getCreateTime());
                    vo.setEndTime(s.getEndTime());
                    vo.setArchiveStatus(s.getArchiveStatus());
                    vo.setArchiveRemark(s.getArchiveRemark());
                    vo.setArchivedAt(s.getArchivedAt());
                    vo.setTitle(s.getTitle());
                    vo.setPriority(s.getPriority());
                    vo.setCategory(s.getCategory());
                    vo.setMetadataUpdatedAt(s.getMetadataUpdatedAt());
                    vo.setTags(tagsBySessionId.getOrDefault(s.getId(), List.of()));
                    vo.setUnreadCount(unreadCounts.getOrDefault(s.getId(), 0L));
                    if (messageMapper != null) {
                        List<com.example.customerservice.domain.ChatMessage> messages = messageMapper.selectLatestHistory(s.getId(), 1);
                        if (!messages.isEmpty()) {
                            var last = messages.get(0);
                            vo.setLastMessageContent(last.getContent());
                            vo.setLastMessageSenderId(last.getSenderId());
                            vo.setLastMessageTime(last.getCreateTime());
                        }
                    }
                    return vo;
                })
                .toList();

        return new PageResult<>(
                page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession> buildSessionScopeQuery(
            String participantId,
            SessionParticipantType participantType,
            String statusFilter,
            String archiveStatusFilter,
            boolean filterArchive,
            boolean filterUnarchived
    ) {
        var query = Wrappers.<ChatSession>lambdaQuery();
        switch (participantType) {
            case USER -> query.eq(ChatSession::getUserId, participantId);
            case AGENT -> query.eq(ChatSession::getAgentId, participantId);
            default -> throw new IllegalArgumentException("不支持的participantType：" + participantType);
        }
        return query
                .eq(statusFilter != null && !statusFilter.isBlank(),
                        ChatSession::getStatus, statusFilter)
                .and(filterArchive && !filterUnarchived, w ->
                        w.eq(ChatSession::getArchiveStatus, archiveStatusFilter))
                .and(filterUnarchived, w ->
                        w.isNull(ChatSession::getArchiveStatus));
    }

    // ── 归档状态（Zendesk 风格） ────────────────────────────────────────────

    @Override
    @Transactional
    public void setArchiveStatus(String agentId, String sessionId, SessionArchiveDTO request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!ChatConstants.SESSION_STATUS_CLOSED.equals(session.getStatus())) {
            throw new BusinessStateException("只能对已结束的会话进行归档");
        }
        if (!agentId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("只有该会话的客服可以归档");
        }
        String target = request.getArchiveStatus();
        if (!ChatConstants.canTransitionTo(session.getArchiveStatus(), target)) {
            throw new BusinessStateException(
                    "不允许从 " + (session.getArchiveStatus() == null ? "未归档" : session.getArchiveStatus())
                            + " 转换到 " + target);
        }
        session.setArchivedBy(agentId);
        session.setArchivedAt(LocalDateTime.now());
        /* 使用 WHERE archive_status = :expected 防止并发覆盖 */
        String previousStatus = session.getArchiveStatus();
        session.setArchiveStatus(target);
        var update = Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, sessionId);
        if (previousStatus == null) {
            update.isNull(ChatSession::getArchiveStatus);
        } else {
            update.eq(ChatSession::getArchiveStatus, previousStatus);
        }
        var archiveUpdate = update
                .set(ChatSession::getArchiveStatus, target)
                .set(ChatSession::getArchivedBy, agentId)
                .set(ChatSession::getArchivedAt, LocalDateTime.now());
        if (request.getRemark() != null) {
            session.setArchiveRemark(request.getRemark());
            archiveUpdate.set(ChatSession::getArchiveRemark, request.getRemark());
        }
        int updated = sessionMapper.update(session, archiveUpdate);
        if (updated != 1) {
            throw new BusinessStateException("归档状态已被其他操作修改，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void saveArchiveRemark(String agentId, String sessionId, SessionArchiveRemarkDTO request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        if (!ChatConstants.SESSION_STATUS_CLOSED.equals(session.getStatus())) {
            throw new BusinessStateException("只能对已结束的会话保存归档备注");
        }
        if (!agentId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("只有该会话的客服可以保存归档备注");
        }
        String previousRemark = session.getArchiveRemark();
        String remark = request.getRemark() == null || request.getRemark().isBlank()
                ? null
                : request.getRemark().trim();
        session.setArchiveRemark(remark);
        var update = Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getAgentId, agentId)
                .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_CLOSED);
        if (previousRemark == null) {
            update.isNull(ChatSession::getArchiveRemark);
        } else {
            update.eq(ChatSession::getArchiveRemark, previousRemark);
        }
        int updated = sessionMapper.update(session, update.set(ChatSession::getArchiveRemark, remark));
        if (updated != 1) {
            throw new BusinessStateException("归档备注已被其他操作修改，请刷新后重试");
        }
    }

    // ── 归档统计（管理端） ──────────────────────────────────────────────────

    @Override
    public ArchiveStatsVO findArchiveStats() {
        ArchiveStatsVO vo = new ArchiveStatsVO();
        Long completed = sessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_CLOSED)
                        .eq(ChatSession::getArchiveStatus, ChatConstants.ARCHIVE_COMPLETED));
        Long pending = sessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_CLOSED)
                        .eq(ChatSession::getArchiveStatus, ChatConstants.ARCHIVE_PENDING));
        Long onHold = sessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_CLOSED)
                        .eq(ChatSession::getArchiveStatus, ChatConstants.ARCHIVE_ON_HOLD));
        Long other = sessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_CLOSED)
                        .eq(ChatSession::getArchiveStatus, ChatConstants.ARCHIVE_OTHER));
        Long unarchived = sessionMapper.selectCount(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getStatus, ChatConstants.SESSION_STATUS_CLOSED)
                        .isNull(ChatSession::getArchiveStatus));
        vo.setCompleted(completed == null ? 0 : completed);
        vo.setPending(pending == null ? 0 : pending);
        vo.setOnHold(onHold == null ? 0 : onHold);
        vo.setOther(other == null ? 0 : other);
        vo.setUnarchived(unarchived == null ? 0 : unarchived);
        return vo;
    }

    private Map<String, Long> loadUnreadCounts(List<String> sessionIds, String userId) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows =
                messageReadMapper.countUnreadBySessions(sessionIds, userId);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String sid = (String) row.get("sessionId");
            Number count = (Number) row.get("unreadCount");
            if (sid != null) {
                result.put(sid, count == null ? 0L : count.longValue());
            }
        }
        return result;
    }

    private Map<String, List<String>> loadSessionTags(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ChatSessionTag> tags = requireTagMapper().selectBySessionIds(sessionIds);
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new HashMap<>();
        for (ChatSessionTag tag : tags) {
            if (tag.getSessionId() == null || tag.getTag() == null) {
                continue;
            }
            result.computeIfAbsent(tag.getSessionId(), ignored -> new ArrayList<>())
                    .add(tag.getTag());
        }
        return result;
    }
}
