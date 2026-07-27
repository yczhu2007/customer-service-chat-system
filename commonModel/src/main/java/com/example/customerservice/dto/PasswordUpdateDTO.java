package com.example.customerservice.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改系统用户密码请求。
 */
public class PasswordUpdateDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度必须在6到128个字符之间")
    private String password;


    public String getPassword() {

        return password;
    }


    public void setPassword(
            String password
    ) {

        this.password = password;
    }
}
