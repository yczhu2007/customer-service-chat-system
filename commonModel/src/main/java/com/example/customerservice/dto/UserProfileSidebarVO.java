package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客服侧展示的用户信息侧栏。 */
public class UserProfileSidebarVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private int vipLevel;
    private int totalSessionCount;
    private LocalDateTime lastSessionTime;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getVipLevel() { return vipLevel; }
    public void setVipLevel(int vipLevel) { this.vipLevel = vipLevel; }
    public int getTotalSessionCount() { return totalSessionCount; }
    public void setTotalSessionCount(int totalSessionCount) { this.totalSessionCount = totalSessionCount; }
    public LocalDateTime getLastSessionTime() { return lastSessionTime; }
    public void setLastSessionTime(LocalDateTime lastSessionTime) { this.lastSessionTime = lastSessionTime; }
}
