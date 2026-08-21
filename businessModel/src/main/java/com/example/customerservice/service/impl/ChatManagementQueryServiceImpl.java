package com.example.customerservice.service.impl;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AdminDashboardVO;
import com.example.customerservice.dto.AgentDashboardVO;
import com.example.customerservice.dto.AgentLoadVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatManagementMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatManagementQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ChatManagementQueryServiceImpl implements ChatManagementQueryService {

    private static final long DASHBOARD_SESSION_LIMIT = 100L;

    private final ChatManagementMapper managementMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatRedisRepository redisRepository;

    public ChatManagementQueryServiceImpl(
            ChatManagementMapper managementMapper,
            ChatSessionMapper sessionMapper,
            ChatRedisRepository redisRepository
    ) {
        this.managementMapper = managementMapper;
        this.sessionMapper = sessionMapper;
        this.redisRepository = redisRepository;
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
                0L,
                DASHBOARD_SESSION_LIMIT
        );
        return new AgentDashboardVO(
                valueOrZero(redisRepository.sortedSetCardinality(RedisConstants.AGENT_LOAD)),
                valueOrZero(redisRepository.sortedSetCardinality(RedisConstants.QUEUE_PENDING)),
                activeSessions == null ? List.of() : List.copyOf(activeSessions),
                managementMapper.countTodayClosedSessionsByAgent(agentId, dayStart, dayEnd)
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
            String userId,
            String agentId,
            String status,
            String archiveStatus,
            Integer rating,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            long pageNo,
            long pageSize
    ) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(100L, pageSize));
        validateStatus(status);
        validateArchiveStatus(archiveStatus);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("rating必须在1到5之间");
        }
        validateTimeRange(fromTime, toTime);

        String normalizedUserId = normalize(userId);
        String normalizedAgentId = normalize(agentId);
        String normalizedStatus = normalize(status);
        String normalizedArchiveStatus = normalize(archiveStatus);
        long total = managementMapper.countSessionSummaries(
                normalizedUserId,
                normalizedAgentId,
                normalizedStatus,
                normalizedArchiveStatus,
                rating,
                fromTime,
                toTime
        );
        long pages = total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize;
        List<SessionSummaryVO> records = total == 0
                ? List.of()
                : managementMapper.findSessionSummaries(
                        normalizedUserId,
                        normalizedAgentId,
                        normalizedStatus,
                        normalizedArchiveStatus,
                        rating,
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
