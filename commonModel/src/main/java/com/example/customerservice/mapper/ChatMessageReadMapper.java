package com.example.customerservice.mapper;

import com.example.customerservice.dto.MessageReadWatermark;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    MessageReadWatermark findLastReadWatermark(
            @Param("sessionId") String sessionId,
            @Param("readerId") String readerId
    );

    /** 批量查询多个会话的未读消息数，返回 sessionId → 未读数。 */
    List<Map<String, Object>> countUnreadBySessions(
            @Param("sessionIds") List<String> sessionIds,
            @Param("userId") String userId
    );
}
