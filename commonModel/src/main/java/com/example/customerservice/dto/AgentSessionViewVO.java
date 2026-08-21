package com.example.customerservice.dto;

import java.io.Serializable;

/**
 * 坐席固定视图定义。
 */
public record AgentSessionViewVO(
        String code,
        String label
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
