package com.example.customerservice.controller;

import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.IChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private IChatService chatService;
    @Mock private IAuthenticationService authenticationService;
    @Mock private CurrentUser currentUser;
    @InjectMocks private ChatController controller;

    @Test
    void ordinaryUserCanRestartConsultationWithoutReconnect() {
        Principal principal = () -> "U001";
        when(authenticationService.findRoleCodesByUserId("U001"))
                .thenReturn(Set.of("USER"));

        controller.startConsultation(principal);

        verify(authenticationService).requirePermission(
                "U001",
                "chat:user:access"
        );
        verify(chatService).onUserConnected("U001");
    }

    @Test
    void agentCannotUseUserRestartConsultationEntry() {
        Principal principal = () -> "A001";
        when(authenticationService.findRoleCodesByUserId("A001"))
                .thenReturn(Set.of("AGENT"));

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.startConsultation(principal)
        );

        verify(chatService, never()).onUserConnected("A001");
    }
}
