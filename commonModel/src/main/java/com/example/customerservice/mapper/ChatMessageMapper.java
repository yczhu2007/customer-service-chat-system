package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.ChatMessage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    ChatMessage findByClientMessage(
            @Param("sessionId") String sessionId,
            @Param("senderId") String senderId,
            @Param("clientMsgId") String clientMsgId
    );

    int recallOwnMessage(
            @Param("messageId") String messageId,
            @Param("senderId") String senderId,
            @Param("recalledAt") LocalDateTime recalledAt,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    List<ChatMessage> selectLatestHistoryBySessionIds(
            @Param("sessionIds") List<String> sessionIds
    );

    List<ChatMessage> selectLatestHistory(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    /** Returns messages strictly before the composite (createTime, id) cursor. */
    List<ChatMessage> selectHistoryBeforeCursor(
            @Param("sessionId") String sessionId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );
}
