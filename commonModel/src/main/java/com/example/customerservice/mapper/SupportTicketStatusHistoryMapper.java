package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SupportTicketStatusHistory;
import com.example.customerservice.dto.SupportTicketStatusHistoryVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SupportTicketStatusHistoryMapper extends BaseMapper<SupportTicketStatusHistory> {
    List<SupportTicketStatusHistoryVO> findRecentByTicketId(@Param("ticketId") Long ticketId);
    long countByTicketId(@Param("ticketId") Long ticketId);
    List<SupportTicketStatusHistoryVO> findByTicketId(
            @Param("ticketId") Long ticketId, @Param("offset") long offset, @Param("pageSize") long pageSize);
}
