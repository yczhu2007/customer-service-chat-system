package com.example.customerservice.service.impl;

import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatSessionTag;
import com.example.customerservice.dto.AdminDashboardVO;
import com.example.customerservice.dto.AgentDashboardVO;
import com.example.customerservice.dto.AgentLoadVO;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatMessageSearchVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.constant.SupportTicketStatus;
import com.example.customerservice.mapper.ChatManagementMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionTagMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatManagementQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChatManagementQueryServiceImpl implements ChatManagementQueryService {

    private static final long DASHBOARD_SESSION_LIMIT = 100L;

    private final ChatManagementMapper managementMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatRedisRepository redisRepository;
    private final ChatSessionTagMapper sessionTagMapper;

    @Autowired
    public ChatManagementQueryServiceImpl(
            ChatManagementMapper managementMapper,
            ChatSessionMapper sessionMapper,
            ChatRedisRepository redisRepository,
            ChatSessionTagMapper sessionTagMapper
    ) {
        this.managementMapper = managementMapper;
        this.sessionMapper = sessionMapper;
        this.redisRepository = redisRepository;
        this.sessionTagMapper = sessionTagMapper;
    }

    @Override
    public AgentDashboardVO findAgentDashboard(String agentId) {
        requireText(agentId, "客服ID不能为空");
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<SessionSummaryVO> activeSessions = managementMapper.findSessionSummaries(
                null,
                agentId,
                ChatConstants.SESSION_STATUS_ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                DASHBOARD_SESSION_LIMIT
        );
        return new AgentDashboardVO(
                valueOrZero(redisRepository.sortedSetCardinality(RedisConstants.AGENT_LOAD)),
                valueOrZero(redisRepository.sortedSetCardinality(RedisConstants.QUEUE_PENDING)),
                activeSessions == null ? List.of() : List.copyOf(activeSessions),
                managementMapper.countTodayClosedSessionsByAgent(agentId, dayStart, dayEnd),
                managementMapper.countAgentTicketsByStatus(agentId, SupportTicketStatus.OPEN.name()),
                managementMapper.countAgentTicketsByStatus(agentId, SupportTicketStatus.IN_PROGRESS.name()),
                managementMapper.countAgentTicketsByStatus(agentId, SupportTicketStatus.WAITING_USER.name())
        );
    }

    @Override
    public List<AgentSessionViewCountVO> findAgentSessionViews(String agentId) {
        requireText(agentId, "客服ID不能为空");
        String normalizedAgentId = agentId.trim();
        List<AgentSessionViewCountVO> counts = managementMapper.countAgentSessionViews(
                normalizedAgentId
        );
        Map<String, Long> countByCode = new LinkedHashMap<>();
        if (counts != null) {
            for (AgentSessionViewCountVO count : counts) {
                if (count != null && count.code() != null) {
                    countByCode.put(count.code(), count.count());
                }
            }
        }
        List<AgentSessionViewCountVO> result = new ArrayList<>(AgentSessionView.values().length);
        for (AgentSessionView view : AgentSessionView.values()) {
            result.add(new AgentSessionViewCountVO(
                    view.getCode(),
                    view.getLabel(),
                    countByCode.getOrDefault(view.getCode(), 0L)
            ));
        }
        return List.copyOf(result);
    }

    @Override
    public PageResult<ChatSessionListItemVO> findAgentViewSessions(
            String agentId,
            AgentSessionView view,
            String ticketStatus,
            long pageNo,
            long pageSize
    ) {
        requireText(agentId, "客服ID不能为空");
        if (view == null) {
            throw new IllegalArgumentException("坐席会话视图不能为空");
        }

        String normalizedAgentId = agentId.trim();
        String normalizedTicketStatus = validateTicketStatus(ticketStatus);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));
        long total = managementMapper.countAgentViewSessions(
                normalizedAgentId,
                view.getCode(),
                normalizedTicketStatus
        );
        long pages = total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize;
        if (total == 0) {
            return new PageResult<>(
                    normalizedPageNo,
                    normalizedPageSize,
                    0L,
                    0L,
                    List.of()
            );
        }

        List<ChatSessionListItemVO> records = managementMapper.findAgentViewSessions(
                normalizedAgentId,
                view.getCode(),
                normalizedTicketStatus,
                (normalizedPageNo - 1) * normalizedPageSize,
                normalizedPageSize
        );
        List<ChatSessionListItemVO> safeRecords = records == null
                ? List.of()
                : List.copyOf(records);
        attachSessionTags(safeRecords);
        return new PageResult<>(
                normalizedPageNo,
                normalizedPageSize,
                total,
                pages,
                safeRecords
        );
    }

    @Override
    public AdminDashboardVO findAdminDashboard() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        Set<String> onlineAgentIds = redisRepository.sortedSetRange(
                RedisConstants.AGENT_LOAD,
                0,
                -1
        );
        List<AgentLoadVO> agentLoads = onlineAgentIds == null || onlineAgentIds.isEmpty()
                ? List.of()
                : managementMapper.findAgentLoads(List.copyOf(onlineAgentIds));
        return new AdminDashboardVO(
                onlineAgentIds == null ? 0L : onlineAgentIds.size(),
                valueOrZero(redisRepository.sortedSetCardinality(RedisConstants.QUEUE_PENDING)),
                managementMapper.countTodaySessions(dayStart, dayEnd),
                managementMapper.countTodayMessages(dayStart, dayEnd),
                agentLoads == null ? List.of() : List.copyOf(agentLoads)
        );
    }

    @Override
    public RatingSummaryVO findRatingSummary(
            String agentId,
            LocalDateTime fromTime,
            LocalDateTime toTime
    ) {
        validateTimeRange(fromTime, toTime);
        return managementMapper.findRatingSummary(normalize(agentId), fromTime, toTime);
    }

    @Override
    public PageResult<SessionSummaryVO> searchSessions(
            String userLoginNumber,
            String agentLoginNumber,
            String status,
            String archiveStatus,
            Integer rating,
            String ticketNo,
            String ticketStatus,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            long pageNo,
            long pageSize
    ) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));
        validateStatus(status);
        validateArchiveStatus(archiveStatus);
        Long ticketId = parseTicketNo(ticketNo);
        String normalizedTicketStatus = validateTicketStatus(ticketStatus);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("rating必须在1到5之间");
        }
        validateTimeRange(fromTime, toTime);

        String normalizedUserLoginNumber = normalize(userLoginNumber);
        String normalizedAgentLoginNumber = normalize(agentLoginNumber);
        String normalizedStatus = normalize(status);
        String normalizedArchiveStatus = normalize(archiveStatus);
        long total = managementMapper.countSessionSummaries(
                normalizedUserLoginNumber,
                normalizedAgentLoginNumber,
                normalizedStatus,
                normalizedArchiveStatus,
                rating,
                ticketId,
                normalizedTicketStatus,
                fromTime,
                toTime
        );
        long pages = total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize;
        List<SessionSummaryVO> records = total == 0
                ? List.of()
                : managementMapper.findSessionSummaries(
                        normalizedUserLoginNumber,
                        normalizedAgentLoginNumber,
                        normalizedStatus,
                        normalizedArchiveStatus,
                        rating,
                        ticketId,
                        normalizedTicketStatus,
                        fromTime,
                        toTime,
                        (normalizedPageNo - 1) * normalizedPageSize,
                        normalizedPageSize
                );
        return new PageResult<>(
                normalizedPageNo,
                normalizedPageSize,
                total,
                pages,
                records == null ? List.of() : List.copyOf(records)
        );
    }

    @Override
    public PageResult<ChatMessageSearchVO> searchMessages(String keyword, String agentId, long pageNo, long pageSize) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword == null) throw new IllegalArgumentException("Search keyword cannot be empty");
        if (normalizedKeyword.length() > 100) throw new IllegalArgumentException("Search keyword cannot exceed 100 characters");
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));
        String normalizedAgentId = normalize(agentId);
        long total = managementMapper.countMessageSearch(normalizedKeyword, normalizedAgentId);
        List<ChatMessageSearchVO> records = total == 0 ? List.of() : managementMapper.findMessageSearch(normalizedKeyword, normalizedAgentId, (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);
        return new PageResult<>(normalizedPageNo, normalizedPageSize, total, total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize, records == null ? List.of() : List.copyOf(records));
    }
    @Override
    public List<SessionTransferLogVO> findTransferLogs(
            String requesterId,
            boolean canViewAllSessions,
            String sessionId
    ) {
        requireText(requesterId, "当前用户不能为空");
        requireText(sessionId, "sessionId不能为空");
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new NotFoundException("会话不存在");
        }
        List<SessionTransferLogVO> records = managementMapper.findTransferLogs(sessionId);
        List<SessionTransferLogVO> safeRecords = records == null
                ? List.of()
                : List.copyOf(records);
        boolean participatedInTransfer = safeRecords.stream().anyMatch(log ->
                requesterId.equals(log.sourceAgentId())
                        || requesterId.equals(log.targetAgentId())
        );
        if (!canViewAllSessions
                && !requesterId.equals(session.getAgentId())
                && !participatedInTransfer) {
            throw new IllegalArgumentException("无权查看该会话的转接记录");
        }
        return safeRecords;
    }

    private void attachSessionTags(List<ChatSessionListItemVO> records) {
        if (records.isEmpty()) {
            return;
        }
        if (sessionTagMapper == null) {
            throw new IllegalStateException(
                    "Agent session views require the ChatSessionTagMapper dependency"
            );
        }

        List<String> sessionIds = new ArrayList<>(records.size());
        for (ChatSessionListItemVO record : records) {
            if (record != null && record.getSessionId() != null) {
                sessionIds.add(record.getSessionId());
            }
        }
        Map<String, List<String>> tagsBySessionId = new LinkedHashMap<>();
        if (!sessionIds.isEmpty()) {
            List<ChatSessionTag> sessionTags = sessionTagMapper.selectBySessionIds(sessionIds);
            if (sessionTags != null) {
                for (ChatSessionTag sessionTag : sessionTags) {
                    if (sessionTag == null
                            || sessionTag.getSessionId() == null
                            || sessionTag.getTag() == null) {
                        continue;
                    }
                    tagsBySessionId.computeIfAbsent(
                            sessionTag.getSessionId(),
                            ignored -> new ArrayList<>()
                    ).add(sessionTag.getTag());
                }
            }
        }
        for (ChatSessionListItemVO record : records) {
            if (record == null || record.getSessionId() == null) {
                continue;
            }
            record.setTags(tagsBySessionId.getOrDefault(record.getSessionId(), List.of()));
        }
    }

    private void validateStatus(String status) {
        String normalized = normalize(status);
        if (normalized != null
                && !ChatConstants.SESSION_STATUS_ACTIVE.equals(normalized)
                && !ChatConstants.SESSION_STATUS_CLOSED.equals(normalized)) {
            throw new IllegalArgumentException("status只允许ACTIVE或CLOSED");
        }
    }

    private void validateArchiveStatus(String archiveStatus) {
        String normalized = normalize(archiveStatus);
        if (normalized != null
                && !"NONE".equals(normalized)
                && !ChatConstants.ARCHIVE_COMPLETED.equals(normalized)
                && !ChatConstants.ARCHIVE_PENDING.equals(normalized)
                && !ChatConstants.ARCHIVE_ON_HOLD.equals(normalized)
                && !ChatConstants.ARCHIVE_OTHER.equals(normalized)) {
            throw new IllegalArgumentException(
                    "archiveStatus只允许NONE、COMPLETED、PENDING、ON_HOLD或OTHER"
            );
        }
    }

    private void validateTimeRange(LocalDateTime fromTime, LocalDateTime toTime) {
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
    }

    private String validateTicketStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) return null;
        try {
            return SupportTicketStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("工单状态不合法");
        }
    }

    private Long parseTicketNo(String ticketNo) {
        String normalized = normalize(ticketNo);
        if (normalized == null) return null;
        if (!normalized.matches("TK-\\d{8}")) {
            throw new IllegalArgumentException("工单编号格式不合法");
        }
        return Long.parseLong(normalized.substring(3));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
