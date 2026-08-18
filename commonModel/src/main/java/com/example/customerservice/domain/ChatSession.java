package com.example.customerservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("chat_session")
public class ChatSession {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String userId;
    private String agentId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    /** 归档状态：COMPLETED / PENDING / ON_HOLD / OTHER，NULL 表示未归档。 */
    private String archiveStatus;
    /** 归档备注（OTHER 时建议必填，其余可选）。 */
    private String archiveRemark;
    /** 归档操作客服 ID。 */
    private String archivedBy;
    /** 归档时间。 */
    private LocalDateTime archivedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getArchiveStatus() {
        return archiveStatus;
    }

    public void setArchiveStatus(String archiveStatus) {
        this.archiveStatus = archiveStatus;
    }

    public String getArchiveRemark() {
        return archiveRemark;
    }

    public void setArchiveRemark(String archiveRemark) {
        this.archiveRemark = archiveRemark;
    }

    public String getArchivedBy() {
        return archivedBy;
    }

    public void setArchivedBy(String archivedBy) {
        this.archivedBy = archivedBy;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    @Override
    public String toString() {
        return "ChatSession{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", status='" + status + '\'' +
                ", archiveStatus='" + archiveStatus + '\'' +
                ", createTime=" + createTime +
                ", endTime=" + endTime +
                '}';
    }
}
