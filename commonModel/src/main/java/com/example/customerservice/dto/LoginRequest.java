package com.example.customerservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** Login credentials received from the client. */
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128个字符")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
