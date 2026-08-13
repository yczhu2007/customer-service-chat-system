package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatMessage;

import java.util.List;
import java.io.Serializable;

/** 聊天历史分页查询结果。 */
public record ChatHistoryPage(
        List<ChatMessage> records,
        long total,
        long pageSize,
        String nextCursor,
        boolean hasMore,
        long unreadCount
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
