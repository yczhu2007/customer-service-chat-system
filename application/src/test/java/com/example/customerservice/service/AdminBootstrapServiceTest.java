package com.example.customerservice.service;

import com.example.customerservice.constant.AccountStatus;
import com.example.customerservice.domain.SysRole;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapServiceTest {

    @Test
    void existingAdministratorOnlyNeedsRoleAssignment() {
        SysUserMapper users = mock(SysUserMapper.class);
        SysRoleMapper roles = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoles = mock(SysUserRoleMapper.class);
        SysRole adminRole = new SysRole();
        adminRole.setId("R1");
        adminRole.setStatus(AccountStatus.ENABLED);
        SysUser admin = new SysUser();
        admin.setId("U1");
        admin.setStatus(AccountStatus.ENABLED);
        when(roles.findByCode("ADMIN")).thenReturn(adminRole);
        when(users.findByUsername("admin")).thenReturn(admin);

        new AdminBootstrapService(users, roles, userRoles)
                .initialize("admin", "admin", "");

        verify(userRoles).assignRole("U1", "R1");
    }
}
