package com.example.customerservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user")
public class SysUser {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String username;

    private String nickname;

    private String password;

    private String status;

    private Integer vipLevel;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


    public String getId() {

        return id;
    }


    public void setId(
            String id
    ) {

        this.id = id;
    }


    public String getUsername() {

        return username;
    }


    public void setUsername(
            String username
    ) {

        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }


    public String getPassword() {

        return password;
    }


    public void setPassword(
            String password
    ) {

        this.password = password;
    }


    public String getStatus() {

        return status;
    }


    public void setStatus(
            String status
    ) {

        this.status = status;
    }

    public Integer getVipLevel() {
        return vipLevel;
    }

    public void setVipLevel(Integer vipLevel) {
        this.vipLevel = vipLevel;
    }


    public LocalDateTime getCreateTime() {

        return createTime;
    }


    public void setCreateTime(
            LocalDateTime createTime
    ) {

        this.createTime = createTime;
    }


    public LocalDateTime getUpdateTime() {

        return updateTime;
    }


    public void setUpdateTime(
            LocalDateTime updateTime
    ) {

        this.updateTime = updateTime;
    }


    @Override
    public String toString() {

        return "SysUser{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", nickname='" + nickname + '\'' +
                ", password='******'" +
                ", status='" + status + '\'' +
                ", vipLevel=" + vipLevel +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
