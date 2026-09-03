package com.example.customerservice.controller;

import com.example.customerservice.dto.WebSocketTicketResponse;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IAuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAuthenticationControllerTest {

    @Mock private IAuthenticationService authenticationService;
    @Mock private CurrentUser currentUser;
    @Mock private WebSocketTicketResponse ticket;
    @InjectMocks private ChatAuthenticationController controller;

    @Test
    void issueWebSocketTicketUsesTheAuthenticatedUser() {
        when(currentUser.getUserId()).thenReturn("U001");
        when(authenticationService.issueWebSocketTicket("Bearer t", "U001")).thenReturn(ticket);

        assertSame(ticket, controller.issueWebSocketTicket("Bearer t").getData());
        verify(authenticationService).issueWebSocketTicket("Bearer t", "U001");
    }
}
