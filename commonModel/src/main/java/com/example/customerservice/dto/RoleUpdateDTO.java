package com.example.customerservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 修改角色请求。
 *
 * 所有字段均允许不提交，
 * 但提交的字段必须符合格式要求。
 */
public class RoleUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "角色编码不能是空字符串"
    )
    @Size(
            max = 64,
            message = "角色编码长度不能超过64个字符"
    )
    private String roleCode;

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "角色名称不能是空字符串"
    )
    @Size(
            max = 64,
            message = "角色名称长度不能超过64个字符"
    )
    private String roleName;

    @Size(
            max = 255,
            message = "角色说明长度不能超过255个字符"
    )
    private String description;

    @Pattern(
            regexp = "(?i)ENABLED|DISABLED",
            message = "角色状态只能是ENABLED或DISABLED"
    )
    private String status;

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
}
