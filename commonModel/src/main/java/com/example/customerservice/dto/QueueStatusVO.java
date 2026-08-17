package com.example.customerservice.dto;

import java.io.Serializable;

/** 排队状态与客服在线概况。 */
public class QueueStatusVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前在线客服数。 */
    private long onlineAgentCount;
    /** 排队中的用户数。 */
    private long queueSize;
    /** 当前用户在队列中的位置（从 1 开始），不在队列中时为 null。 */
    private Long myPosition;
    /** 预估等待秒数，无法预估时为 null。 */
    private Long estimatedWaitSeconds;

    public long getOnlineAgentCount() { return onlineAgentCount; }
    public void setOnlineAgentCount(long onlineAgentCount) { this.onlineAgentCount = onlineAgentCount; }
    public long getQueueSize() { return queueSize; }
    public void setQueueSize(long queueSize) { this.queueSize = queueSize; }
    public Long getMyPosition() { return myPosition; }
    public void setMyPosition(Long myPosition) { this.myPosition = myPosition; }
    public Long getEstimatedWaitSeconds() { return estimatedWaitSeconds; }
    public void setEstimatedWaitSeconds(Long estimatedWaitSeconds) { this.estimatedWaitSeconds = estimatedWaitSeconds; }
}
