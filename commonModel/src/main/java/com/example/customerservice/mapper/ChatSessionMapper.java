package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.ChatSession;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    ChatSession findActiveByUserId(String userId);

    int endSession(
            @Param("id") String id,
            @Param("endTime") LocalDateTime endTime
    );

    int transferSession(
            @Param("id") String id,
            @Param("sourceAgentId") String sourceAgentId,
            @Param("targetAgentId") String targetAgentId
    );

}

