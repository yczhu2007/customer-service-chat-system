package com.example.customerservice.dto;

import java.io.Serializable;

public record RatingSummaryVO(
        String agentId,
        long ratingCount,
        double averageRating,
        long fiveStarCount
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
