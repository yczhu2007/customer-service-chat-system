package com.example.customerservice.service;

import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.dto.AdminDashboardVO;
import com.example.customerservice.dto.AgentDashboardVO;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatMessageSearchVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.RatingSummaryVO;
import com.example.customerservice.dto.SessionSummaryVO;
import com.example.customerservice.dto.SessionTransferLogVO;

import java.time.LocalDateTime;
import java.util.List;

/** 客服工作台和管理员质检统计查询。 */
public interface ChatManagementQueryService {

    AgentDashboardVO findAgentDashboard(String agentId);

    List<AgentSessionViewCountVO> findAgentSessionViews(String agentId);

    PageResult<ChatSessionListItemVO> findAgentViewSessions(
            String agentId,
            AgentSessionView view,
            String ticketStatus,
            String ticketKeyword,
            long pageNo,
            long pageSize
    );

    AdminDashboardVO findAdminDashboard();

    RatingSummaryVO findRatingSummary(
            String agentId,
            LocalDateTime fromTime,
            LocalDateTime toTime
    );

    PageResult<SessionSummaryVO> searchSessions(
            String userLoginNumber,
            String agentLoginNumber,
            String status,
            String archiveStatus,
            Integer rating,
            String ticketNo,
            String ticketStatus,
            Boolean hasTicket,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            long pageNo,
            long pageSize
    );

    PageResult<ChatMessageSearchVO> searchMessages(String keyword, String agentId, long pageNo, long pageSize);

    List<SessionTransferLogVO> findTransferLogs(
            String requesterId,
            boolean canViewAllSessions,
            String sessionId
    );
}
