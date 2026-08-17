package com.example.customerservice.dto;

import java.io.Serializable;

/** 注册结果，恢复码只在本次响应中明文返回。 */
public record UserRegistrationVO(UserVO user, String recoveryCode) implements Serializable {
    private static final long serialVersionUID = 1L;
}
