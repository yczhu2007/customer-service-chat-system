package com.example.customerservice.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
/**
 * 创建或修改权限请求。
 */
public class PermissionCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    @Size(
            max = 64,
            message = "权限ID长度不能超过64个字符"
    )
    private String id;

    @NotBlank(
            message = "权限编码不能为空"
    )
    @Size(
            max = 128,
            message = "权限编码长度不能超过128个字符"
    )
    private String permissionCode;

    @NotBlank(
            message = "权限名称不能为空"
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
