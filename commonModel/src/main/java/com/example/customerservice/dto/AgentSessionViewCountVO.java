package com.example.customerservice.dto;

import java.io.Serializable;

/**
 * 坐席固定视图计数。
 */
public record AgentSessionViewCountVO(
        String code,
        String label,
        long count
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
