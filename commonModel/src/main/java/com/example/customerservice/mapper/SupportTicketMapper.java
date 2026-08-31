package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SupportTicket;
import com.example.customerservice.dto.AdminSupportTicketListItemVO;
import com.example.customerservice.dto.AdminSupportTicketQueryDTO;
import com.example.customerservice.dto.SupportTicketStatusCountVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SupportTicketMapper extends BaseMapper<SupportTicket> {
    long countAdminTickets(@Param("query") AdminSupportTicketQueryDTO query);

    List<AdminSupportTicketListItemVO> findAdminTickets(
            @Param("query") AdminSupportTicketQueryDTO query,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );

    List<SupportTicketStatusCountVO> findAdminTicketStatusCounts(@Param("query") AdminSupportTicketQueryDTO query);
}
