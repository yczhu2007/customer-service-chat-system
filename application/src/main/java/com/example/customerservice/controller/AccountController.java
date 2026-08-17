package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.dto.ProfilePasswordUpdateDTO;
import com.example.customerservice.dto.PasswordResetDTO;
import com.example.customerservice.dto.ProfileUpdateDTO;
import com.example.customerservice.dto.RecoveryCodeVO;
import com.example.customerservice.dto.UserRegisterDTO;
import com.example.customerservice.dto.UserRegistrationVO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.security.AuthRateLimiter;
import com.example.customerservice.service.UserAccountService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final UserAccountService accountService;
    private final CurrentUser currentUser;
    private final AuthRateLimiter authRateLimiter;

    public AccountController(UserAccountService accountService, CurrentUser currentUser,
                             AuthRateLimiter authRateLimiter) {
        this.accountService = accountService;
        this.currentUser = currentUser;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserRegistrationVO> register(@Valid @RequestBody UserRegisterDTO request, HttpServletRequest servletRequest) {
        authRateLimiter.checkRegistration(servletRequest);
        return Result.success(HttpStatus.CREATED.value(), "注册成功", accountService.register(request));
    }

    @PostMapping("/forgot-password")
    public Result<RecoveryCodeVO> resetPassword(
            @Valid @RequestBody PasswordResetDTO request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkPasswordReset(servletRequest);
        return Result.success(HttpStatus.OK.value(), "密码重置成功，请保存新的恢复码",
                accountService.resetPassword(request));
    }

    @PostMapping("/recovery-code")
    public Result<RecoveryCodeVO> regenerateRecoveryCode() {
        return Result.success(HttpStatus.OK.value(), "新的恢复码已生成，请妥善保存",
                accountService.regenerateRecoveryCode(currentUser.getUserId()));
    }

    @GetMapping("/profile")
    public Result<UserVO> profile() { return Result.success(accountService.getProfile(currentUser.getUserId())); }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateDTO request) {
        return Result.success(accountService.updateProfile(currentUser.getUserId(), request));
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody ProfilePasswordUpdateDTO request) {
        accountService.updatePassword(currentUser.getUserId(), request);
        return Result.successMessage("密码修改成功，请重新登录");
    }
}
