package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.LoginRequest;
import com.example.customerservice.dto.LoginResponse;
import com.example.customerservice.dto.WebSocketTicketResponse;
import com.example.customerservice.security.AuthRateLimiter;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@Validated
public class ChatAuthenticationController {

    @Autowired
    private IAuthenticationService authenticationService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private AuthRateLimiter authRateLimiter;

    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkLogin(servletRequest);
        return Result.success(authenticationService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        authenticationService.logout(authorization, currentUser.getUserId());
        return Result.successMessage("退出登录成功");
    }

    @PostMapping("/ws-ticket")
    public Result<WebSocketTicketResponse> issueWebSocketTicket(
            @RequestHeader("Authorization") String authorization
    ) {
        return Result.success(authenticationService.issueWebSocketTicket(
                authorization, currentUser.getUserId()));
    }
}
