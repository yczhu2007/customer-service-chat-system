package com.example.customerservice.service;

import com.example.customerservice.constant.RoleCodes;
import com.example.customerservice.constant.AccountStatus;
import com.example.customerservice.domain.SysRole;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class AdminBootstrapService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public AdminBootstrapService(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Transactional
    public void initialize(String adminId, String username, String password) {
        SysRole adminRole = roleMapper.findByCode(RoleCodes.ADMIN);
        if (adminRole == null) throw new IllegalStateException("ADMIN角色不存在，请先执行rbac_ddl.sql");
        if (!AccountStatus.ENABLED.equals(adminRole.getStatus())) {
            throw new IllegalStateException("ADMIN角色已被禁用，无法初始化默认管理员");
        }

        SysUser adminUser = userMapper.findByUsername(username);
        if (adminUser == null) {
            createAdmin(adminId, username, password);
            adminUser = userMapper.findByUsername(username);
            if (adminUser == null) throw new IllegalStateException("默认管理员创建后查询失败");
            log.info("默认管理员创建成功，username={}", username);
        } else {
            log.info("默认管理员已经存在，username={}", username);
        }

        userRoleMapper.assignRole(adminUser.getId(), adminRole.getId());
        log.info("默认管理员ADMIN角色检查完成，userId={}", adminUser.getId());
        if (!AccountStatus.ENABLED.equals(adminUser.getStatus())) {
            log.warn("默认管理员当前已被禁用，userId={}", adminUser.getId());
        }
    }

    private void createAdmin(String adminId, String username, String password) {
        String normalizedId = requireText(adminId, "默认管理员ID不能为空");
        String normalizedUsername = requireText(username, "默认管理员用户名不能为空");
        String normalizedPassword = requireText(
                password,
                "默认管理员不存在，请通过ADMIN_BOOTSTRAP_PASSWORD环境变量设置初始密码"
        );
        if (userMapper.selectById(normalizedId) != null) {
            throw new IllegalStateException("默认管理员ID已经被其他用户占用：" + normalizedId);
        }
        SysUser admin = new SysUser();
        admin.setId(normalizedId);
        admin.setUsername(normalizedUsername);
        admin.setNickname(normalizedUsername);
        admin.setPassword(PasswordUtil.hash(normalizedPassword));
        admin.setStatus(AccountStatus.ENABLED);
        if (userMapper.insert(admin) != 1) throw new IllegalStateException("创建默认管理员失败");
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalStateException(message);
        return value.trim();
    }
}
