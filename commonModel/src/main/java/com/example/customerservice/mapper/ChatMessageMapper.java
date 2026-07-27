package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.ChatMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 根据会话ID查询全部消息，
     * 按照创建时间从早到晚排列
     */
    List<ChatMessage> findBySessionId(
            @Param("sessionId") String sessionId
    );
}
