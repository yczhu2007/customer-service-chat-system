package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record SessionTransferLogVO(
        String id,
        String sessionId,
        String sourceAgentId,
        String targetAgentId,
        LocalDateTime createTime
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
