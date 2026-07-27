package com.example.customerservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_role")
public class SysRole {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String roleCode;

    private String roleName;

    private String description;

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


    public String getRoleCode() {

        return roleCode;
    }


    public void setRoleCode(
            String roleCode
    ) {

        this.roleCode = roleCode;
    }


    public String getRoleName() {

        return roleName;
    }


    public void setRoleName(
            String roleName
    ) {

        this.roleName = roleName;
    }


    public String getDescription() {

        return description;
    }


    public void setDescription(
            String description
    ) {

        this.description = description;
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

        return "SysRole{" +
                "id='" + id + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", roleName='" + roleName + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
