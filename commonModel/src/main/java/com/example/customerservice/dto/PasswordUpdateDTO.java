package com.example.customerservice.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 修改系统用户密码请求。
 */
public class PasswordUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "新密码长度必须在8到128个字符之间")
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
