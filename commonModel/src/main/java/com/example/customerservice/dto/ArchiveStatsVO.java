package com.example.customerservice.dto;

import java.io.Serializable;

/** 归档统计概览（管理端）。 */
public class ArchiveStatsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已完成数。 */
    private long completed;
    /** 挂起·等客户回复数。 */
    private long pending;
    /** 挂起·等内部处理数。 */
    private long onHold;
    /** 其他情况数。 */
    private long other;
    /** 未归档（已结束但未打标签）数。 */
    private long unarchived;

    public long getCompleted() { return completed; }
    public void setCompleted(long completed) { this.completed = completed; }
    public long getPending() { return pending; }
    public void setPending(long pending) { this.pending = pending; }
    public long getOnHold() { return onHold; }
    public void setOnHold(long onHold) { this.onHold = onHold; }
    public long getOther() { return other; }
    public void setOther(long other) { this.other = other; }
    public long getUnarchived() { return unarchived; }
    public void setUnarchived(long unarchived) { this.unarchived = unarchived; }
}
