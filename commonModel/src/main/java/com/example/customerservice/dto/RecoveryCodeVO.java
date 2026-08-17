package com.example.customerservice.dto;

import java.io.Serializable;

/** 只展示一次的账号恢复码。 */
public record RecoveryCodeVO(String recoveryCode) implements Serializable {
    private static final long serialVersionUID = 1L;
}
