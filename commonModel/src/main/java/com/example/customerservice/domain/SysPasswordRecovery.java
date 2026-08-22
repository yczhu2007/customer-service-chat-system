package com.example.customerservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_password_recovery")
public class SysPasswordRecovery {
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;
    private String recoveryHash;
    private LocalDateTime expiresTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRecoveryHash() { return recoveryHash; }
    public void setRecoveryHash(String recoveryHash) { this.recoveryHash = recoveryHash; }
    public LocalDateTime getExpiresTime() { return expiresTime; }
    public void setExpiresTime(LocalDateTime expiresTime) { this.expiresTime = expiresTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "SysPasswordRecovery{" +
                "userId='" + userId + '\'' +
                ", recoveryHash='[REDACTED]'" +
                ", expiresTime=" + expiresTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
