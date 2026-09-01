package com.example.customerservice.dto;

import java.util.List;

/** 管理员报表页面一次加载所需的聚合数据。 */
public record AdminReportOverviewVO(
        List<TimeBucketCountVO> sessionTrend,
        List<AgentReceptionRankVO> agentReceptionRanking,
        Long averageFirstResponseSeconds,
        List<DurationBucketCountVO> sessionDurationDistribution,
        SatisfactionMetricsVO satisfaction,
        List<SupportTicketStatusCountVO> ticketStatusDistribution
) {
    public record TimeBucketCountVO(String bucket, long count) { }

    public record AgentReceptionRankVO(String agentId, String agentName, long sessionCount) { }

    public record DurationBucketCountVO(String bucket, long count) { }

    public record SatisfactionMetricsVO(
            long ratingCount,
            Double averageRating,
            long lowRatingCount,
            Double lowRatingRate
    ) { }
}
