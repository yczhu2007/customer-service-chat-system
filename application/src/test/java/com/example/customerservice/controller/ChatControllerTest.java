package com.example.customerservice.controller;

import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.HistoryRequest;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.IAuthenticationService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private ChatRoutingOperations chatRoutingOperations;
    @Mock private ChatMessageOperations chatMessageOperations;
    @Mock private IAuthenticationService authenticationService;
    @Mock private Validator validator;
    @InjectMocks private ChatController controller;

    @Test
    void ordinaryUserCanStartConsultationExplicitly() {
        Principal principal = () -> "U001";
        when(authenticationService.findRoleCodesByUserId("U001")).thenReturn(Set.of("USER"));

        controller.startConsultation(principal);

        verify(authenticationService).requirePermission("U001", "chat:user:access");
        verify(chatRoutingOperations).onUserConnected("U001");
    }

    @Test
    void historyResponseEchoesTheClientRequestId() {
        HistoryRequest request = new HistoryRequest();
        request.setSessionId("S001");
        request.setRequestId("history-1");
        request.setPageSize(20);
        when(chatMessageOperations.getHistory("S001", "U001", null, 20))
                .thenReturn(new ChatHistoryPage(List.of(), 0, 20, null, false, 0));

        Map<String, Object> response = controller.getHistory(request, () -> "U001");

        assertEquals("history-1", response.get("requestId"));
    }
}
