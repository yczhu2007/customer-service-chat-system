package com.example.customerservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;

/**
 * 修改系统用户请求。
 *
 * 修改接口允许只提交需要修改的字段，
 * 但提交的字段必须符合格式要求。
 */
public class UserUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    @Min(value = 0, message = "VIP等级不能小于0")
    @Max(value = 5, message = "VIP等级不能大于5")
    private Integer vipLevel;

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

    public Integer getVipLevel() {
        return vipLevel;
    }

    public void setVipLevel(Integer vipLevel) {
        this.vipLevel = vipLevel;
    }
}
