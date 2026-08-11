package com.example.customerservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;


/**
 * 修改权限请求。
 *
 * 所有字段都允许不提供，
 * 但只要提供，就必须符合格式要求。
 *
 * “至少提供一个修改字段”的判断，
 * 继续由RoleServiceImpl负责。
 */
public class PermissionUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "权限编码不能是空字符串"
    )
    @Size(
            max = 128,
            message = "权限编码长度不能超过128个字符"
    )
    private String permissionCode;


    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "权限名称不能是空字符串"
    )
    @Size(
            max = 64,
            message = "权限名称长度不能超过64个字符"
    )
    private String permissionName;


    @Pattern(
            regexp = "(?i)API|MENU|BUTTON",
            message = "权限类型只能是API、MENU或BUTTON"
    )
    private String permissionType;


    /*
     * 允许传入空字符串，
     * 空字符串表示清除原来的请求方法。
     */
    @Pattern(
            regexp = "(?i)^$|GET|POST|PUT|DELETE|PATCH",
            message = "请求方法只能是GET、POST、PUT、DELETE或PATCH"
    )
    private String requestMethod;


    @Size(
            max = 255,
            message = "请求路径长度不能超过255个字符"
    )
    private String requestPath;


    @Size(
            max = 255,
            message = "权限说明长度不能超过255个字符"
    )
    private String description;


    @Pattern(
            regexp = "(?i)ENABLED|DISABLED",
            message = "权限状态只能是ENABLED或DISABLED"
    )
    private String status;


    public String getPermissionCode() {
        return permissionCode;
    }


    public void setPermissionCode(
            String permissionCode
    ) {
        this.permissionCode =
                permissionCode;
    }


    public String getPermissionName() {
        return permissionName;
    }


    public void setPermissionName(
            String permissionName
    ) {
        this.permissionName =
                permissionName;
    }


    public String getPermissionType() {
        return permissionType;
    }


    public void setPermissionType(
            String permissionType
    ) {
        this.permissionType =
                permissionType;
    }


    public String getRequestMethod() {
        return requestMethod;
    }


    public void setRequestMethod(
            String requestMethod
    ) {
        this.requestMethod =
                requestMethod;
    }


    public String getRequestPath() {
        return requestPath;
    }


    public void setRequestPath(
            String requestPath
    ) {
        this.requestPath =
                requestPath;
    }


    public String getDescription() {
        return description;
    }


    public void setDescription(
            String description
    ) {
        this.description =
                description;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(
            String status
    ) {
        this.status =
                status;
    }
}
