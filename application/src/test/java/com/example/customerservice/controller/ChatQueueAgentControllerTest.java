package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.MessagePersistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatQueueAgentControllerTest {

    @Mock private ChatRoutingOperations chatRoutingOperations;
    @Mock private ChatAgentOperations chatAgentOperations;
    @Mock private MessagePersistService messagePersistService;
    @Mock private CurrentUser currentUser;
    @InjectMocks private ChatQueueAgentController controller;

    @Test
    void queueCancellationReturnsBusinessResultFromRoutingService() {
        when(currentUser.getUserId()).thenReturn("U001");
        when(chatRoutingOperations.cancelWaitingUser("U001")).thenReturn(true);

        Result<Void> result = controller.cancelQueue();

        assertEquals("已取消排队", result.getMessage());
        verify(chatRoutingOperations).cancelWaitingUser("U001");
    }

    @Test
    void vipSkillUsesAgentLoginNumberAtBusinessBoundary() {
        controller.enableAgentVipSkill("agent-login");

        verify(chatAgentOperations).setAgentVipSkillByLoginNumber("agent-login", true);
    }
}
