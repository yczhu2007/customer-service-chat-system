package com.example.customerservice.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 聊天消息已读状态持久化。 */
public interface ChatMessageReadMapper {

    int markReadThrough(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId,
            @Param("lastReadMessageId") String lastReadMessageId,
            @Param("readTime") LocalDateTime readTime
    );

    long countUnread(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId
    );
}
