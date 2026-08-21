package com.example.customerservice.mapper;

import com.example.customerservice.domain.ChatSessionTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionTagMapper {

    int insert(ChatSessionTag sessionTag);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    List<ChatSessionTag> selectBySessionId(@Param("sessionId") String sessionId);
}
