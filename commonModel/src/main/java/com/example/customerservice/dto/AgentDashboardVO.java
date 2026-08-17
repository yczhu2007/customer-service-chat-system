package com.example.customerservice.dto;

import java.io.Serializable;
import java.util.List;

public record AgentDashboardVO(
        long onlineAgentCount,
        long queueSize,
        List<SessionSummaryVO> myActiveSessions,
        long todayClosedCount
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
