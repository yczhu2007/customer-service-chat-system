package com.example.customerservice.listener;

import com.example.customerservice.config.WebSocketUserPrincipal;
import com.example.customerservice.monitoring.ChatMonitoringMetrics;
import com.example.customerservice.service.ChatPresenceOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectEvent;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class WebSocketEventListenerTest {

    @Test
    void authenticatedUserConnectionIsRegisteredInMonitoring() {
        ChatMonitoringMetrics metrics = new ChatMonitoringMetrics(new SimpleMeterRegistry());
        WebSocketEventListener listener = new WebSocketEventListener(
                mock(ChatPresenceOperations.class),
                metrics
        );
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.SESSION_ID_HEADER, "ws-1")
                .setHeader(SimpMessageHeaderAccessor.USER_HEADER,
                        new WebSocketUserPrincipal("U001", Set.of("USER")))
                .build();

        listener.connect(new SessionConnectEvent(this, message));

        assertEquals(1, metrics.userConnections());
    }

    @Test
    void localConnectionMetricSurvivesRedisPresenceFailure() {
        ChatMonitoringMetrics metrics = new ChatMonitoringMetrics(new SimpleMeterRegistry());
        ChatPresenceOperations presenceOperations = mock(ChatPresenceOperations.class);
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(presenceOperations).registerOnline("U001", "ws-1");
        WebSocketEventListener listener = new WebSocketEventListener(presenceOperations, metrics);
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.SESSION_ID_HEADER, "ws-1")
                .setHeader(SimpMessageHeaderAccessor.USER_HEADER,
                        new WebSocketUserPrincipal("U001", Set.of("USER")))
                .build();

        assertThrows(IllegalStateException.class,
                () -> listener.connect(new SessionConnectEvent(this, message)));

        assertEquals(1, metrics.userConnections());
    }
}
