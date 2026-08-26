package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.impl.UserServiceImpl;
import com.example.customerservice.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplQueryTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final UserServiceImpl service = new UserServiceImpl(
            userMapper,
            mock(SysRoleMapper.class),
            mock(SysUserRoleMapper.class),
            tokenService
    );

    @BeforeEach
    void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysUser.class
        );
    }

    @Test
    void findPageFiltersByLoginNumberOrNickname() throws Exception {
        when(userMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SysUser> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        Method findPage = IUserService.class.getMethod(
                "findPage", long.class, long.class, String.class
        );
        findPage.invoke(service, 1L, 20L, " alice ");

        ArgumentCaptor<LambdaQueryWrapper<SysUser>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(queryCaptor.getValue().getParamNameValuePairs().containsValue("%alice%"));
        assertTrue(sqlSegment.contains("username"));
        assertTrue(sqlSegment.contains("nickname"));
    }

    @Test
    void administratorResetUsesTheFixedPasswordAndRevokesExistingTokens() {
        SysUser user = new SysUser();
        user.setId("U001");
        when(userMapper.selectById("U001")).thenReturn(user);
        when(userMapper.updatePassword(org.mockito.ArgumentMatchers.eq("U001"), anyString())).thenReturn(1);

        service.resetPasswordByAdmin("U001");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(org.mockito.ArgumentMatchers.eq("U001"), passwordCaptor.capture());
        assertTrue(PasswordUtil.matches("12345678", passwordCaptor.getValue()));
        verify(tokenService).revokeAllForUser("U001");
    }
}
