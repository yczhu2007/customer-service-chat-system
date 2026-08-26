package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.SupportTicketCreateDTO;
import com.example.customerservice.dto.SupportTicketUpdateDTO;
import com.example.customerservice.dto.SupportTicketVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.SupportTicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** 会话关联工单接口。 */
@RestController
@RequestMapping("/chat")
@Validated
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final CurrentUser currentUser;

    public SupportTicketController(
            SupportTicketService supportTicketService,
            CurrentUser currentUser
    ) {
        this.supportTicketService = supportTicketService;
        this.currentUser = currentUser;
    }

    @GetMapping("/sessions/{sessionId}/ticket")
    public Result<SupportTicketVO> findTicket(
            @PathVariable @NotBlank @Size(max = 64) String sessionId
    ) {
        boolean administrator = currentUser.getRoleCodes().contains("ADMIN");
        return Result.success(supportTicketService.findBySessionId(
                currentUser.getUserId(), administrator, sessionId
        ));
    }

    @PostMapping("/sessions/{sessionId}/ticket")
    public Result<SupportTicketVO> createTicket(
            @PathVariable @NotBlank @Size(max = 64) String sessionId,
            @Valid @RequestBody SupportTicketCreateDTO request
    ) {
        currentUser.requireRole("AGENT");
        return Result.success(supportTicketService.createTicket(
                currentUser.getUserId(), sessionId, request
        ));
    }

    @PatchMapping("/tickets/{ticketNo}")
    public Result<SupportTicketVO> updateTicket(
            @PathVariable @Pattern(regexp = "TK-\\d{8}") String ticketNo,
            @Valid @RequestBody SupportTicketUpdateDTO request
    ) {
        currentUser.requireRole("AGENT");
        return Result.success(supportTicketService.updateTicket(
                currentUser.getUserId(), ticketNo, request
        ));
    }
}
