package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.ChatMessage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    int editOwnMessage(
            @Param("messageId") String messageId,
            @Param("senderId") String senderId,
            @Param("content") String content,
            @Param("editedAt") LocalDateTime editedAt,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    int recallOwnMessage(
            @Param("messageId") String messageId,
            @Param("senderId") String senderId,
            @Param("recalledAt") LocalDateTime recalledAt,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    List<ChatMessage> selectLatestHistory(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    List<ChatMessage> selectHistoryAtCursorTime(
            @Param("sessionId") String sessionId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    List<ChatMessage> selectHistoryBeforeTime(
            @Param("sessionId") String sessionId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("limit") int limit
    );
}
