package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.AdminSupportTicketListItemVO;
import com.example.customerservice.dto.AdminSupportTicketQueryDTO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.SupportTicketStatusCountVO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.dto.SupportTicketStatusHistoryVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.SupportTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketControllerTest {

    @Mock private SupportTicketService supportTicketService;
    @Mock private CurrentUser currentUser;
    @Mock private HttpServletRequest httpServletRequest;
    @InjectMocks private SupportTicketController controller;

    @Test
    void userWorkspaceDoesNotUseAdminAuditAccessForMultiRoleAccount() {
        SupportTicketVO ticket = new SupportTicketVO();
        when(currentUser.getUserId()).thenReturn("U001");
        when(currentUser.getRoleCodes()).thenReturn(Set.of("USER", "ADMIN"));
        when(httpServletRequest.getHeader("X-Workspace-Role")).thenReturn("USER");
        when(supportTicketService.findBySessionId("U001", false, "S001")).thenReturn(ticket);

        Result<SupportTicketVO> result = controller.findTicket("S001");

        assertEquals(ticket, result.getData());
        verify(supportTicketService).findBySessionId("U001", false, "S001");
    }

    @Test
    void assignedAgentCanCreateTicket() {
        SupportTicketCreateDTO request = new SupportTicketCreateDTO();
        request.setDescription("支付失败");
        SupportTicketVO ticket = new SupportTicketVO();
        when(currentUser.getUserId()).thenReturn("A001");
        when(supportTicketService.createTicket("A001", "S001", request)).thenReturn(ticket);

        Result<SupportTicketVO> result = controller.createTicket("S001", request);

        assertEquals(ticket, result.getData());
        verify(currentUser).requireRole("AGENT");
        verify(supportTicketService).createTicket("A001", "S001", request);
    }

    @Test
    void administratorCanListTicketsAndReadFilteredStatusCounts() {
        AdminSupportTicketQueryDTO query = new AdminSupportTicketQueryDTO();
        PageResult<AdminSupportTicketListItemVO> page = new PageResult<>(1, 20, 0, 0, List.of());
        List<SupportTicketStatusCountVO> counts = List.of(new SupportTicketStatusCountVO("OPEN", 1));
        when(supportTicketService.findAdminTickets(query)).thenReturn(page);
        when(supportTicketService.findAdminTicketStatusCounts(query)).thenReturn(counts);

        assertEquals(page, controller.findAdminTickets(query).getData());
        assertEquals(counts, controller.findAdminTicketStatusCounts(query).getData());

        verify(currentUser, org.mockito.Mockito.times(2)).requireRole("ADMIN");
        verify(supportTicketService).findAdminTickets(query);
        verify(supportTicketService).findAdminTicketStatusCounts(query);
    }

    @Test
    void assignedAgentCanUpdateTicket() {
        SupportTicketUpdateDTO request = new SupportTicketUpdateDTO();
        request.setStatus("IN_PROGRESS");
        request.setDescription("支付失败");
        request.setVersion(0);
        SupportTicketVO ticket = new SupportTicketVO();
        when(currentUser.getUserId()).thenReturn("A001");
        when(supportTicketService.updateTicket("A001", "TK-00000125", request)).thenReturn(ticket);

        Result<SupportTicketVO> result = controller.updateTicket("TK-00000125", request);

        assertEquals(ticket, result.getData());
        verify(currentUser).requireRole("AGENT");
        verify(supportTicketService).updateTicket("A001", "TK-00000125", request);
    }

    @Test
    void sessionParticipantCanReadTicketHistory() {
        SupportTicketStatusHistoryVO history = new SupportTicketStatusHistoryVO();
        when(currentUser.getUserId()).thenReturn("U001");
        when(currentUser.getRoleCodes()).thenReturn(Set.of("USER"));
        when(supportTicketService.findHistoryBySessionId("U001", false, "S001"))
                .thenReturn(List.of(history));

        Result<List<SupportTicketStatusHistoryVO>> result = controller.findTicketHistory("S001");

        assertEquals(List.of(history), result.getData());
        verify(supportTicketService).findHistoryBySessionId("U001", false, "S001");
    }
}
