package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** 管理员手工创建会话请求。账号字段使用登录编号。 */
public class AdminSessionCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户登录编号不能为空")
    @Size(max = 64, message = "用户登录编号长度不能超过64个字符")
    private String userLoginNumber;

    @NotBlank(message = "客服登录编号不能为空")
    @Size(max = 64, message = "客服登录编号长度不能超过64个字符")
    private String agentLoginNumber;

    public String getUserLoginNumber() { return userLoginNumber; }
    public void setUserLoginNumber(String userLoginNumber) { this.userLoginNumber = userLoginNumber; }
    public String getAgentLoginNumber() { return agentLoginNumber; }
    public void setAgentLoginNumber(String agentLoginNumber) { this.agentLoginNumber = agentLoginNumber; }
}
