package com.example.customerservice.dto;

import java.time.LocalDateTime;

/** 管理员报表的时间范围和统计粒度。 */
public class AdminReportQueryDTO {
    private LocalDateTime from;
    private LocalDateTime to;
    private String granularity;

    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }
    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
}
