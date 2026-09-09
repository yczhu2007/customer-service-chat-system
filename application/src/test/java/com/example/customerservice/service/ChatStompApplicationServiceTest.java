package com.example.customerservice.service;

import com.example.customerservice.dto.TypingRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatStompApplicationServiceTest {

    @Mock private ChatRoutingOperations chatRoutingOperations;
    @Mock private ChatMessageOperations chatMessageOperations;
    @Mock private ChatSessionOperations chatSessionOperations;
    @Mock private ChatPresenceOperations chatPresenceOperations;
    @Mock private IAuthenticationService authenticationService;
    @Mock private Validator validator;
    @InjectMocks private ChatStompApplicationService service;

    @Test
    void handleTypingRejectsMissingSessionId() {
        Principal principal = () -> "U001";

        assertThrows(IllegalArgumentException.class,
                () -> service.handleTyping(new TypingRequest(null, true), principal));

        verifyNoInteractions(chatPresenceOperations);
    }
}
