package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 会话历史列表项。 */
public class ChatSessionListItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String userId;
    private String agentId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private String lastMessageContent;
    private String lastMessageSenderId;
    private LocalDateTime lastMessageTime;
    private String title;
    private String priority;
    private String category;
    private List<String> tags;
    private LocalDateTime metadataUpdatedAt;
    /** 当前用户在该会话中的未读消息数。 */
    private long unreadCount;
    /** 归档状态（COMPLETED/PENDING/ON_HOLD/OTHER），NULL 表示未归档。 */
    private String archiveStatus;
    /** 归档备注。 */
    private String archiveRemark;
    /** 归档时间。 */
    private LocalDateTime archivedAt;
    private String ticketStatus;
    private String ticketNo;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getLastMessageContent() { return lastMessageContent; }
    public void setLastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; }
    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public LocalDateTime getMetadataUpdatedAt() { return metadataUpdatedAt; }
    public void setMetadataUpdatedAt(LocalDateTime metadataUpdatedAt) { this.metadataUpdatedAt = metadataUpdatedAt; }
    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }
    public String getArchiveStatus() { return archiveStatus; }
    public void setArchiveStatus(String archiveStatus) { this.archiveStatus = archiveStatus; }
    public String getArchiveRemark() { return archiveRemark; }
    public void setArchiveRemark(String archiveRemark) { this.archiveRemark = archiveRemark; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
}
