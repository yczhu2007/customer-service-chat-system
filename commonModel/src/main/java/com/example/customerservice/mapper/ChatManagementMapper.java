package com.example.customerservice.mapper;

import com.example.customerservice.dto.AgentLoadVO;
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

    List<AgentLoadVO> findAgentLoads(@Param("agentIds") List<String> agentIds);

    long countSessionSummaries(
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("status") String status,
            @Param("archiveStatus") String archiveStatus,
            @Param("rating") Integer rating,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    List<SessionSummaryVO> findSessionSummaries(
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("status") String status,
            @Param("archiveStatus") String archiveStatus,
            @Param("rating") Integer rating,
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
}
