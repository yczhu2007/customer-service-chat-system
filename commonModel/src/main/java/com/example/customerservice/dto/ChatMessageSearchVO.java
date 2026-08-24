package com.example.customerservice.dto;

import java.time.LocalDateTime;

/** A paginated message keyword search result. */
public record ChatMessageSearchVO(
        String messageId, String sessionId, String userId, String agentId,
        String senderId, String senderRole, String type, String content,
        LocalDateTime createTime, String sessionStatus, String sessionTitle
) { }