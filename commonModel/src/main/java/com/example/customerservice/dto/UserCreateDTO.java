package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 创建系统用户请求。
 */
public class UserCreateDTO {

    @Size(max = 64, message = "用户ID长度不能超过64个字符")
    private String id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度必须在6到128个字符之间")
    private String password;

    @Pattern(
            regexp = "(?i)ENABLED|DISABLED",
            message = "用户状态只能是ENABLED或DISABLED"
    )
    private String status;

    @Min(value = 0, message = "VIP等级不能小于0")
    @Max(value = 5, message = "VIP等级不能大于5")
    private Integer vipLevel;

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

    public Integer getVipLevel() {
        return vipLevel;
    }

    public void setVipLevel(Integer vipLevel) {
        this.vipLevel = vipLevel;
    }
}
