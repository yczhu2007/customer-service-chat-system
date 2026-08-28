package com.example.customerservice.dto;

import java.time.LocalDateTime;

public class SupportTicketStatusHistoryVO {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private String operatorNickname;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getOperatorNickname() { return operatorNickname; }
    public void setOperatorNickname(String operatorNickname) { this.operatorNickname = operatorNickname; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
