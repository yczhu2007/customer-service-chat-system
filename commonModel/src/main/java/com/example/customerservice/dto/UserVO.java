package com.example.customerservice.dto;

import java.time.LocalDateTime;
import java.util.Set;


/**
 * 系统用户返回对象。
 *
 * 不包含password，防止密码哈希通过接口泄露。
 */
public class UserVO {

    private String id;

    private String username;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Set<String> roles;

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }


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
}
