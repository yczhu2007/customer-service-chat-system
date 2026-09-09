package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatMessage;

import java.util.List;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 聊天历史分页查询结果。 */
public record ChatHistoryPage(
        List<ChatMessage> records,
        long total,
        long pageSize,
        String nextCursor,
        boolean hasMore,
        long unreadCount,
        String counterpartLastReadMessageId,
        LocalDateTime counterpartLastReadMessageCreateTime,
        LocalDateTime counterpartLastReadAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
