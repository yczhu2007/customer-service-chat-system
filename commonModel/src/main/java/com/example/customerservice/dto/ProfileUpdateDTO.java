package com.example.customerservice.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/** 当前登录用户修改账号资料请求。 */
public class ProfileUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
