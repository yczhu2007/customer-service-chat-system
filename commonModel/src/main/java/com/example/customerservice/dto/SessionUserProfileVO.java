package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record SessionUserProfileVO(
        String userId,
        String username,
        int vipLevel,
        long historySessionCount,
        LocalDateTime lastSessionTime
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
