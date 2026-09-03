package com.example.customerservice.config;

import com.example.customerservice.mapper.SysRolePermissionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock private TokenService tokenService;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private SysRolePermissionMapper sysRolePermissionMapper;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(
                tokenService, sysUserRoleMapper, sysRolePermissionMapper);
        // Per-test user stubs keep Mockito strict-stubbing checks meaningful.
    }

    @Test
    void rejectsUnauthenticatedNonConnectFrame() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", null), null));
    }

    @Test
    void rejectsRevokedTokenForEveryStompCommand() {
        when(tokenService.resolveUserId("revoked-token")).thenReturn(null);
        for (StompCommand command : Set.of(StompCommand.CONNECT, StompCommand.SEND,
                StompCommand.SUBSCRIBE, StompCommand.ACK)) {
            assertThrows(MessageDeliveryException.class,
                    () -> interceptor.preSend(frame(command, destinationFor(command), rawPrincipal(),
                            "revoked-token"), null), command.name());
        }
    }

    @Test
    void allowsDisconnectAfterTokenExpires() {
        assertDoesNotThrow(
                () -> interceptor.preSend(frame(StompCommand.DISCONNECT, null, rawPrincipal(), null), null));
    }

    @Test
    void rejectsClientSendToBrokerDestination() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/topic/chat", validPrincipal()), null));
    }

    @Test
    void rejectsSendOutsideApplicationWhitelist() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/app/admin.delete", principal()), null));
    }

    @Test
    void acceptsWhitelistedApplicationSend() {
        assertDoesNotThrow(
                () -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", principal()), null));
    }

    @Test
    void acceptsValidTokenWithoutLoadingUserStateForEveryFrame() {
        when(tokenService.resolveUserId("valid-token")).thenReturn("U001");

        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/app/chat.send", rawPrincipal()), null));

        org.mockito.Mockito.verifyNoInteractions(sysUserMapper);
    }

    @Test
    void rejectsSubscriptionOutsideUserWhitelist() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chat", principal()), null));
    }

    @Test
    void allowsPermittedUserChatSubscription() {
        when(sysUserRoleMapper.findRoleCodesByUserId("U001")).thenReturn(Set.of("USER"));
        when(sysRolePermissionMapper.findPermissionCodesByUserId("U001"))
                .thenReturn(Set.of("chat:user:access"));

        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/user/queue/chat", principal()), null));
    }

    private WebSocketUserPrincipal principal() {
        when(tokenService.resolveUserId("valid-token")).thenReturn("U001");
        return rawPrincipal();
    }

    private WebSocketUserPrincipal validPrincipal() {
        return principal();
    }

    private WebSocketUserPrincipal rawPrincipal() {
        return new WebSocketUserPrincipal("U001", Set.of("USER"));
    }

    private String destinationFor(StompCommand command) {
        if (command == StompCommand.SEND) {
            return "/app/chat.send";
        }
        return command == StompCommand.SUBSCRIBE ? "/user/queue/messages" : null;
    }

    private org.springframework.messaging.Message<byte[]> frame(
            StompCommand command, String destination, WebSocketUserPrincipal principal) {
        return frame(command, destination, principal, "valid-token");
    }

    private org.springframework.messaging.Message<byte[]> frame(
            StompCommand command, String destination, WebSocketUserPrincipal principal, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setUser(principal);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                WebSocketHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE, token);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
