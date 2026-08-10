package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.ChatMessage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

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
}
