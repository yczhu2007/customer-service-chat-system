package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SupportTicketStatusHistory;
import com.example.customerservice.dto.SupportTicketStatusHistoryVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SupportTicketStatusHistoryMapper extends BaseMapper<SupportTicketStatusHistory> {
    List<SupportTicketStatusHistoryVO> findRecentByTicketId(@Param("ticketId") Long ticketId);
}
