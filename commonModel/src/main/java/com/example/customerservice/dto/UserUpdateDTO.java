package com.example.customerservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改系统用户请求。
 *
 * 修改接口允许只提交需要修改的字段，
 * 但提交的字段必须符合格式要求。
 */
public class UserUpdateDTO {

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "用户名不能是空字符串"
    )
    @Size(
            max = 64,
            message = "用户名长度不能超过64个字符"
    )
    private String username;

    @Pattern(
            regexp = "(?i)ENABLED|DISABLED",
            message = "用户状态只能是ENABLED或DISABLED"
    )
    private String status;

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
}