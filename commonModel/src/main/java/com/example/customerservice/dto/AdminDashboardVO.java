package com.example.customerservice.dto;

import java.io.Serializable;
import java.util.List;

public record AdminDashboardVO(
        long onlineAgentCount,
        long totalQueueSize,
        long todaySessionCount,
        long todayMessageCount,
        List<AgentLoadVO> agentLoads
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
