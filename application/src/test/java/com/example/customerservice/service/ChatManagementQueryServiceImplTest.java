package com.example.customerservice.service;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.AgentLoadVO;
import com.example.customerservice.dto.AdminReportQueryDTO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;
import com.example.customerservice.mapper.ChatManagementMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionTagMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatManagementQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatManagementQueryServiceImplTest {

    @Mock private ChatManagementMapper managementMapper;
    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatSessionTagMapper sessionTagMapper;
    @Mock private ChatRedisRepository redisRepository;

    private ChatManagementQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatManagementQueryServiceImpl(
                managementMapper,
                sessionMapper,
                redisRepository,
                sessionTagMapper
        );
    }

    @Test
    void adminDashboardCombinesRedisPresenceWithDatabaseMetrics() {
        when(redisRepository.sortedSetRange(RedisConstants.AGENT_LOAD, 0, -1))
                .thenReturn(Set.of("A001", "A002"));
        when(redisRepository.sortedSetCardinality(RedisConstants.QUEUE_PENDING))
                .thenReturn(3L);
        when(managementMapper.findAgentLoads(any()))
                .thenReturn(List.of(
                        new AgentLoadVO("A001", "agent001", 1),
                        new AgentLoadVO("A002", "agent002", 2)
                ));
        when(managementMapper.countTodaySessions(any(), any())).thenReturn(8L);
        when(managementMapper.countTodayMessages(any(), any())).thenReturn(42L);

        var dashboard = service.findAdminDashboard();

        assertEquals(2L, dashboard.onlineAgentCount());
        assertEquals(3L, dashboard.totalQueueSize());
        assertEquals(8L, dashboard.todaySessionCount());
        assertEquals(42L, dashboard.todayMessageCount());
        assertEquals(2, dashboard.agentLoads().size());
    }

    @Test
    void agentDashboardIncludesTicketStatusCounts() {
        when(managementMapper.countAgentTicketsByStatus("A001", "OPEN")).thenReturn(2L);
        when(managementMapper.countAgentTicketsByStatus("A001", "IN_PROGRESS")).thenReturn(3L);
        when(managementMapper.countAgentTicketsByStatus("A001", "WAITING_USER")).thenReturn(1L);

        var dashboard = service.findAgentDashboard("A001");

        assertEquals(2L, dashboard.openTicketCount());
        assertEquals(3L, dashboard.inProgressTicketCount());
        assertEquals(1L, dashboard.waitingUserTicketCount());
    }

    @Test
    void agentTicketViewPassesStatusFilterToPaginationQueries() {
        ChatSessionListItemVO item = new ChatSessionListItemVO();
        item.setSessionId("S001");
        item.setTicketStatus("OPEN");
        when(managementMapper.countAgentViewSessions("A001", "MY_TICKETS", "OPEN", "TK-00000125")).thenReturn(1L);
        when(managementMapper.findAgentViewSessions("A001", "MY_TICKETS", "OPEN", "TK-00000125", 0L, 20L))
                .thenReturn(List.of(item));

        var result = service.findAgentViewSessions("A001", AgentSessionView.MY_TICKETS, "OPEN", "TK-00000125", 1, 20);

        assertEquals("OPEN", result.getRecords().get(0).getTicketStatus());
    }

    @Test
    void adminSessionSearchUsesBoundedPagination() {
        when(managementMapper.countSessionSummaries(
                eq("U001"), eq("A001"), eq("CLOSED"),
                eq("COMPLETED"), eq(5), eq(1L), eq(null), eq("RESOLVED"), eq(false), eq(null), eq(null)))
                .thenReturn(1L);
        SessionSummaryVO summary = new SessionSummaryVO(
                "S001", "U001", "user001", "A001", "agent001",
                "CLOSED", "新咨询", LocalDateTime.now(), LocalDateTime.now(),
                "已处理完成", LocalDateTime.now(), 0L, 5, "TK-00000001", "RESOLVED"
        );
        when(managementMapper.findSessionSummaries(
                eq("U001"), eq("A001"), eq("CLOSED"),
                eq("COMPLETED"), eq(5), eq(1L), eq(null), eq("RESOLVED"), eq(false), eq(null), eq(null),
                eq(0L), eq(100L)))
                .thenReturn(List.of(summary));

        PageResult<SessionSummaryVO> result = service.searchSessions(
                "U001", "A001", "CLOSED", "COMPLETED", 5, "TK-00000001", null, "RESOLVED", false,
                null, null, 0, 500
        );

        assertEquals(1L, result.getPageNo());
        assertEquals(100L, result.getPageSize());
        assertEquals(1L, result.getTotal());
        assertEquals("S001", result.getRecords().get(0).sessionId());
    }

    @Test
    void transferSourceAgentCanReadTransferHistoryAfterOwnershipChanged() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setAgentId("A002");
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(managementMapper.findTransferLogs("S001"))
                .thenReturn(List.of(new SessionTransferLogVO(
                        "T001", "S001", "A001", "agent001",
                        "A002", "agent002", "MANUAL_TRANSFER", LocalDateTime.now()
                )));

        List<SessionTransferLogVO> result = service.findTransferLogs(
                "A001", false, "S001"
        );

        assertEquals(1, result.size());
        assertEquals("A002", result.get(0).targetAgentId());
    }

    @Test
    void invalidRatingAndTimeRangeAreRejectedBeforeQueryingDatabase() {
        assertThrows(IllegalArgumentException.class, () ->
                service.searchSessions(
                null, null, null, null, 6,
                        null, null, null,
                        false, null, null, 1, 20
                ));
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () ->
                service.findRatingSummary(null, now, now.minusSeconds(1)));
    }

    @Test
    void ratingSummarySupportsOverallStatistics() {
        RatingSummaryVO summary = new RatingSummaryVO(
                null, 10L, 4.2, 0L, 1L, 2L, 3L, 4L
        );
        when(managementMapper.findRatingSummary(null, null, null))
                .thenReturn(summary);

        RatingSummaryVO result = service.findRatingSummary(null, null, null);

        assertEquals(10L, result.ratingCount());
        verify(managementMapper).findRatingSummary(null, null, null);
    }

    @Test
    void reportOverviewQueriesEveryMetricForRequestedRange() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 1, 0, 0);
        AdminReportQueryDTO query = new AdminReportQueryDTO();
        query.setFrom(from);
        query.setTo(to);
        query.setGranularity("DAY");

        service.findAdminReportOverview(query);

        verify(managementMapper).findSessionTrend(from, to, "DAY");
        verify(managementMapper).findAgentReceptionRanking(from, to, 10);
        verify(managementMapper).findAverageFirstResponseSeconds(from, to);
        verify(managementMapper).findSessionDurationDistribution(from, to);
        verify(managementMapper).findSatisfactionMetrics(from, to);
        verify(managementMapper).findTicketStatusDistribution(from, to);
    }
}
