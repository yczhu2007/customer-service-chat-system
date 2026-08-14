package com.example.customerservice.controller;

import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionOperations;
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

    @Mock private ChatRoutingOperations chatRoutingOperations;
    @Mock private ChatAgentOperations chatAgentOperations;
    @Mock private ChatMessageOperations chatMessageOperations;
    @Mock private ChatSessionOperations chatSessionOperations;
    @Mock private ChatPresenceOperations chatPresenceOperations;
    @Mock private IAuthenticationService authenticationService;
    @Mock private CurrentUser currentUser;
    @InjectMocks private ChatController controller;

    @Test
    void ordinaryUserCanStartConsultationExplicitly() {
        Principal principal = () -> "U001";
        when(authenticationService.findRoleCodesByUserId("U001"))
                .thenReturn(Set.of("USER"));

        controller.startConsultation(principal);

        verify(authenticationService).requirePermission(
                "U001",
                "chat:user:access"
        );
        verify(chatRoutingOperations).onUserConnected("U001");
    }

    @Test
    void agentCannotUseUserConsultationEntry() {
        Principal principal = () -> "A001";
        when(authenticationService.findRoleCodesByUserId("A001"))
                .thenReturn(Set.of("AGENT"));

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.startConsultation(principal)
        );

        verify(chatRoutingOperations, never()).onUserConnected("A001");
    }
}
