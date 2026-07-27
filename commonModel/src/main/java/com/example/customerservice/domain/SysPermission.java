package com.example.customerservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_permission")
public class SysPermission {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String permissionCode;

    private String permissionName;

    private String permissionType;

    private String requestMethod;

    private String requestPath;

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


    public String getPermissionCode() {

        return permissionCode;
    }


    public void setPermissionCode(
            String permissionCode
    ) {

        this.permissionCode = permissionCode;
    }


    public String getPermissionName() {

        return permissionName;
    }


    public void setPermissionName(
            String permissionName
    ) {

        this.permissionName = permissionName;
    }


    public String getPermissionType() {

        return permissionType;
    }


    public void setPermissionType(
            String permissionType
    ) {

        this.permissionType = permissionType;
    }


    public String getRequestMethod() {

        return requestMethod;
    }


    public void setRequestMethod(
            String requestMethod
    ) {

        this.requestMethod = requestMethod;
    }


    public String getRequestPath() {

        return requestPath;
    }


    public void setRequestPath(
            String requestPath
    ) {

        this.requestPath = requestPath;
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

        return "SysPermission{" +
                "id='" + id + '\'' +
                ", permissionCode='" + permissionCode + '\'' +
                ", permissionName='" + permissionName + '\'' +
                ", permissionType='" + permissionType + '\'' +
                ", requestMethod='" + requestMethod + '\'' +
                ", requestPath='" + requestPath + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
