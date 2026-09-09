package com.example.customerservice.config;

import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketHandshakeInterceptorTest {

    @Mock private TokenService tokenService;
    @Mock private IAuthenticationService authenticationService;
    @Mock private WebSocketHandler webSocketHandler;

    @Test
    void handshakeDelegatesEnabledUserValidationToAuthenticationService() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(tokenService.resolveUserId("valid-token")).thenReturn("U001");
        when(authenticationService.findRoleCodesByUserId("U001")).thenReturn(Set.of("USER"));
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(
                tokenService, authenticationService);

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                webSocketHandler,
                new HashMap<>());

        assertTrue(accepted);
        verify(authenticationService).requireEnabledUser("U001");
    }
}
