package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record SessionSummaryVO(
        String sessionId,
        String userId,
        String username,
        String agentId,
        String agentUsername,
        String status,
        String title,
        LocalDateTime createTime,
        LocalDateTime endTime,
        String lastMessage,
        LocalDateTime lastMessageTime,
        long unreadCount,
        Integer rating
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
