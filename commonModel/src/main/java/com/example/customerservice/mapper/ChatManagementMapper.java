package com.example.customerservice.mapper;

import com.example.customerservice.dto.AgentLoadVO;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatMessageSearchVO;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 管理工作台、会话质检和统计查询专用 Mapper。 */
public interface ChatManagementMapper {

    long countTodaySessions(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    long countTodayMessages(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    long countTodayClosedSessionsByAgent(
            @Param("agentId") String agentId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<AgentSessionViewCountVO> countAgentSessionViews(@Param("agentId") String agentId);

    long countAgentViewSessions(
            @Param("agentId") String agentId,
            @Param("viewCode") String viewCode,
            @Param("ticketStatus") String ticketStatus,
            @Param("ticketKeyword") String ticketKeyword
    );

    List<ChatSessionListItemVO> findAgentViewSessions(
            @Param("agentId") String agentId,
            @Param("viewCode") String viewCode,
            @Param("ticketStatus") String ticketStatus,
            @Param("ticketKeyword") String ticketKeyword,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );

    List<AgentLoadVO> findAgentLoads(@Param("agentIds") List<String> agentIds);

    long countAgentTicketsByStatus(@Param("agentId") String agentId, @Param("ticketStatus") String ticketStatus);

    long countSessionSummaries(
            @Param("userLoginNumber") String userLoginNumber,
            @Param("agentLoginNumber") String agentLoginNumber,
            @Param("status") String status,
            @Param("archiveStatus") String archiveStatus,
            @Param("rating") Integer rating,
            @Param("ticketId") Long ticketId,
            @Param("ticketKeyword") String ticketKeyword,
            @Param("ticketStatus") String ticketStatus,
            @Param("hasTicket") Boolean hasTicket,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    List<SessionSummaryVO> findSessionSummaries(
            @Param("userLoginNumber") String userLoginNumber,
            @Param("agentLoginNumber") String agentLoginNumber,
            @Param("status") String status,
            @Param("archiveStatus") String archiveStatus,
            @Param("rating") Integer rating,
            @Param("ticketId") Long ticketId,
            @Param("ticketKeyword") String ticketKeyword,
            @Param("ticketStatus") String ticketStatus,
            @Param("hasTicket") Boolean hasTicket,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );

    RatingSummaryVO findRatingSummary(
            @Param("agentId") String agentId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    List<SessionTransferLogVO> findTransferLogs(@Param("sessionId") String sessionId);

    long countMessageSearch(@Param("keyword") String keyword, @Param("agentId") String agentId);

    List<ChatMessageSearchVO> findMessageSearch(@Param("keyword") String keyword, @Param("agentId") String agentId, @Param("offset") long offset, @Param("pageSize") long pageSize);
}
