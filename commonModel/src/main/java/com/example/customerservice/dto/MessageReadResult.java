package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 消息批量已读结果，同时作为发送给会话双方的已读状态事件。 */
public record MessageReadResult(
        String event,
        String sessionId,
        String readerId,
        String lastReadMessageId,
        long markedCount,
        long unreadCount,
        LocalDateTime readAt
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static MessageReadResult completed(
            String sessionId,
            String readerId,
            String lastReadMessageId,
            long markedCount,
            long unreadCount,
            LocalDateTime readAt
    ) {
        return new MessageReadResult(
                "MESSAGES_READ",
                sessionId,
                readerId,
                lastReadMessageId,
                markedCount,
                unreadCount,
                readAt
        );
    }
}
