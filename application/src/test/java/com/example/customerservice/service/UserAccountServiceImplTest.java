package com.example.customerservice.service;

import com.example.customerservice.domain.SysRole;
import com.example.customerservice.domain.SysPasswordRecovery;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.UserRegisterDTO;
import com.example.customerservice.dto.PasswordResetDTO;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysPasswordRecoveryMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.impl.UserAccountServiceImpl;
import com.example.customerservice.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAccountServiceImplTest {
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysPasswordRecoveryMapper passwordRecoveryMapper = mock(SysPasswordRecoveryMapper.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final UserAccountServiceImpl service = new UserAccountServiceImpl(
            userMapper, roleMapper, userRoleMapper, passwordRecoveryMapper, tokenService, 90);

    @Test
    void registrationCreatesEnabledUserAndAssignsDefaultUserRole() {
        SysRole role = new SysRole(); role.setId("R_USER"); role.setRoleCode("USER"); role.setStatus("ENABLED");
        when(roleMapper.findByCode("USER")).thenReturn(role);
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);
        when(userRoleMapper.assignRole(anyString(), eq("R_USER"))).thenReturn(1);
        when(passwordRecoveryMapper.insert(any(SysPasswordRecovery.class))).thenReturn(1);
        when(userMapper.selectById(anyString())).thenAnswer(invocation -> {
            SysUser user = new SysUser(); user.setId(invocation.getArgument(0)); user.setUsername("new_user");
            user.setStatus("ENABLED"); user.setVipLevel(0); return user;
        });
        when(userRoleMapper.findAllRoleCodesByUserId(anyString())).thenReturn(Set.of("USER"));
        UserRegisterDTO request = new UserRegisterDTO(); request.setUsername("new_user"); request.setPassword("12345678");

        var result = service.register(request);

        assertEquals(Set.of("USER"), result.user().getRoles());
        assertTrue(result.recoveryCode().matches("[0-9A-F]{4}(?:-[0-9A-F]{4}){3}"));
        verify(userRoleMapper).assignRole(anyString(), eq("R_USER"));
    }

    @Test
    void concurrentDuplicateUsernameReturnsBusinessConflict() {
        SysRole role = new SysRole(); role.setId("R_USER"); role.setStatus("ENABLED");
        when(roleMapper.findByCode("USER")).thenReturn(role);
        when(userMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("duplicate"));
        UserRegisterDTO request = new UserRegisterDTO(); request.setUsername("same_name"); request.setPassword("12345678");
        BusinessStateException error = assertThrows(BusinessStateException.class, () -> service.register(request));
        assertEquals("用户名已经存在", error.getMessage());
    }

    @Test
    void validRecoveryCodeResetsPasswordRotatesCodeAndRevokesTokens() {
        SysUser user = new SysUser();
        user.setId("U001"); user.setUsername("user001"); user.setStatus("ENABLED");
        user.setPassword(PasswordUtil.hash("old-password"));
        SysPasswordRecovery recovery = new SysPasswordRecovery();
        recovery.setUserId("U001");
        recovery.setRecoveryHash(PasswordUtil.hash("ABCD-1234-EF56-7890"));
        recovery.setExpiresTime(java.time.LocalDateTime.now().plusDays(1));
        when(userMapper.findByUsername("user001")).thenReturn(user);
        when(passwordRecoveryMapper.selectById("U001")).thenReturn(recovery);
        when(userMapper.updatePassword(eq("U001"), anyString())).thenReturn(1);
        when(passwordRecoveryMapper.updateById(any(SysPasswordRecovery.class))).thenReturn(1);
        PasswordResetDTO request = new PasswordResetDTO();
        request.setUsername("user001");
        request.setRecoveryCode("abcd-1234-ef56-7890");
        request.setNewPassword("new-password-123");

        var result = service.resetPassword(request);

        assertTrue(result.recoveryCode().matches("[0-9A-F]{4}(?:-[0-9A-F]{4}){3}"));
        assertNotEquals("ABCD-1234-EF56-7890", result.recoveryCode());
        verify(tokenService).revokeAllForUser("U001");
    }

    @Test
    void expiredRecoveryCodeCannotResetPassword() {
        SysUser user = new SysUser();
        user.setId("U001"); user.setUsername("user001"); user.setStatus("ENABLED");
        user.setPassword(PasswordUtil.hash("old-password"));
        SysPasswordRecovery recovery = new SysPasswordRecovery();
        recovery.setUserId("U001");
        recovery.setRecoveryHash(PasswordUtil.hash("ABCD-1234-EF56-7890"));
        recovery.setExpiresTime(java.time.LocalDateTime.now().minusSeconds(1));
        when(userMapper.findByUsername("user001")).thenReturn(user);
        when(passwordRecoveryMapper.selectById("U001")).thenReturn(recovery);
        PasswordResetDTO request = new PasswordResetDTO();
        request.setUsername("user001");
        request.setRecoveryCode("ABCD-1234-EF56-7890");
        request.setNewPassword("new-password-123");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPassword(request)
        );

        assertEquals("用户名或恢复码错误", error.getMessage());
        verify(userMapper, never()).updatePassword(anyString(), anyString());
    }
}
