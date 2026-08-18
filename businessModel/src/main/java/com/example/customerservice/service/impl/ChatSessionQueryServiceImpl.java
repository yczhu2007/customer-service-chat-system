package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatSessionRating;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.*;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionRatingMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatSessionQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatSessionQueryServiceImpl implements ChatSessionQueryService {

    private final ChatSessionMapper sessionMapper;
    private final ChatSessionRatingMapper ratingMapper;
    private final SysUserMapper userMapper;
    private final ChatMessageReadMapper messageReadMapper;
    private final ChatRedisRepository chatRedisRepository;
    private final long averageHandleSeconds;

    public ChatSessionQueryServiceImpl(ChatSessionMapper sessionMapper,
                                       ChatSessionRatingMapper ratingMapper,
                                       SysUserMapper userMapper,
                                       ChatMessageReadMapper messageReadMapper,
                                       ChatRedisRepository chatRedisRepository,
                                       @Value("${app.chat.queue.average-handle-seconds:300}") long averageHandleSeconds) {
        this.sessionMapper = sessionMapper;
        this.ratingMapper = ratingMapper;
        this.userMapper = userMapper;
        this.messageReadMapper = messageReadMapper;
        this.chatRedisRepository = chatRedisRepository;
        this.averageHandleSeconds = Math.max(30L, averageHandleSeconds);
    }

    @Override
    public PageResult<ChatSessionListItemVO> findMySessions(
            String participantId, String statusFilter, long pageNo, long pageSize) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));

        Page<ChatSession> page = sessionMapper.selectPage(
                new Page<>(normalizedPageNo, normalizedPageSize),
                Wrappers.<ChatSession>lambdaQuery()
                        .and(w -> w
                                .eq(ChatSession::getUserId, participantId)
                                .or()
                                .eq(ChatSession::getAgentId, participantId)
                        )
                        .eq(statusFilter != null && !statusFilter.isBlank(),
                                ChatSession::getStatus, statusFilter)
                        .orderByDesc(ChatSession::getCreateTime)
                        .orderByDesc(ChatSession::getId)
        );

        List<ChatSessionListItemVO> records = page.getRecords().stream()
                .map(s -> {
                    ChatSessionListItemVO vo = new ChatSessionListItemVO();
                    vo.setSessionId(s.getId());
                    vo.setUserId(s.getUserId());
                    vo.setAgentId(s.getAgentId());
                    vo.setStatus(s.getStatus());
                    vo.setCreateTime(s.getCreateTime());
                    vo.setEndTime(s.getEndTime());
                    try {
                        vo.setUnreadCount(messageReadMapper.countUnread(s.getId(), participantId));
                    } catch (Exception e) {
                        vo.setUnreadCount(0);
                    }
                    return vo;
                })
                .toList();

        return new PageResult<>(
                page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
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
        if (ratingMapper.selectById(sessionId) != null) {
            throw new BusinessStateException("该会话已经评价过");
        }

        ChatSessionRating rating = new ChatSessionRating();
        rating.setSessionId(sessionId);
        rating.setUserId(userId);
        rating.setRating(request.getRating());
        rating.setComment(request.getComment());
        rating.setCreateTime(LocalDateTime.now());
        if (ratingMapper.insert(rating) != 1) {
            throw new IllegalStateException("评价保存失败");
        }
        return toRatingVO(rating);
    }

    @Override
    public SessionRatingVO getSessionRating(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        ChatSessionRating rating = ratingMapper.selectById(sessionId);
        if (rating == null) {
            throw new NotFoundException("该会话尚未评价");
        }
        return toRatingVO(rating);
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
        vo.setVipLevel(user.getVipLevel() == null ? 0 : user.getVipLevel());
        vo.setTotalSessionCount(totalSessions == null ? 0 : totalSessions.intValue());
        vo.setLastSessionTime(lastSession == null ? null : lastSession.getCreateTime());
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
}
