package com.example.customerservice.service;

import com.example.customerservice.dto.AdminDashboardVO;
import com.example.customerservice.dto.AgentDashboardVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;

import java.time.LocalDateTime;
import java.util.List;

/** 客服工作台和管理员质检统计查询。 */
public interface ChatManagementQueryService {

    AgentDashboardVO findAgentDashboard(String agentId);

    AdminDashboardVO findAdminDashboard();

    RatingSummaryVO findRatingSummary(
            String agentId,
            LocalDateTime fromTime,
            LocalDateTime toTime
    );

    PageResult<SessionSummaryVO> searchSessions(
            String userId,
            String agentId,
            String status,
            String archiveStatus,
            Integer rating,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            long pageNo,
            long pageSize
    );

    List<SessionTransferLogVO> findTransferLogs(
            String requesterId,
            boolean canViewAllSessions,
            String sessionId
    );
}
