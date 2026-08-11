package com.example.customerservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.io.Serializable;

/**
 * 登录请求和登录成功响应共用的数据传输对象。
 *
 * 请求字段：username、password。
 * 响应字段：token、tokenType、expiresInSeconds、userId、username、roles。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128个字符")
    private String password;

    private String token;

    private String tokenType;

    private long expiresInSeconds;

    private String userId;

    private Set<String> roles;

    public LoginDTO() {
    }

    public static LoginDTO success(
            String token,
            String tokenType,
            long expiresInSeconds,
            String userId,
            String username,
            Set<String> roles
    ) {
        LoginDTO result = new LoginDTO();
        result.setToken(token);
        result.setTokenType(tokenType);
        result.setExpiresInSeconds(expiresInSeconds);
        result.setUserId(userId);
        result.setUsername(username);
        result.setRoles(roles);
        return result;
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

    public String getToken() {
        return token;
    }

    public void setToken(
            String token
    ) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(
            String tokenType
    ) {
        this.tokenType = tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(
            long expiresInSeconds
    ) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(
            String userId
    ) {
        this.userId = userId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(
            Set<String> roles
    ) {
        this.roles = roles;
    }
}
