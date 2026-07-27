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

    private String password;

    private String status;

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
                ", password='******'" +
                ", status='" + status + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
