package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatMessage;

import java.util.List;

/** 聊天历史分页查询结果。 */
public record ChatHistoryPage(
        List<ChatMessage> records,
        long total,
        long pageNo,
        long pageSize,
        long pages,
        long unreadCount
) {
}
