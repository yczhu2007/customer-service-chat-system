package com.example.customerservice.dto;

import java.time.LocalDateTime;

/** 会话对端已读到的最新消息及其阅读时间。 */
public record MessageReadWatermark(
        String messageId,
        LocalDateTime messageCreateTime,
        LocalDateTime readAt
) { }
