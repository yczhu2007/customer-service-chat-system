package com.example.customerservice.dto;

import java.io.Serializable;

public record AgentLoadVO(String agentId, String username, long activeSessionCount)
        implements Serializable {
    private static final long serialVersionUID = 1L;
}
