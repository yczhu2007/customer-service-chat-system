package com.example.customerservice.runner;

import com.example.customerservice.domain.SysRole;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统启动时初始化默认管理员。
 *
 * 功能：
 * 1. 检查ADMIN角色是否存在；
 * 2. 检查默认管理员是否存在；
 * 3. 不存在时创建默认管理员；
 * 4. 确认默认管理员拥有ADMIN角色；
 * 5. 重复启动不会重复创建数据。
 */
@Component
public class AdminBootstrapRunner
        implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AdminBootstrapRunner.class
            );

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    @Value(
            "${app.bootstrap.admin.enabled:true}"
    )
    private boolean enabled;

    @Value(
            "${app.bootstrap.admin.id:admin}"
    )
    private String adminId;

    @Value(
            "${app.bootstrap.admin.username:admin}"
    )
    private String adminUsername;

    @Value(
            "${app.bootstrap.admin.password:}"
    )
    private String adminPassword;

    public AdminBootstrapRunner(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper =
                sysUserRoleMapper;
    }

    @Override
    @Transactional
    public void run(
            ApplicationArguments arguments
    ) {
        if (!enabled) {
            LOGGER.info(
                    "默认管理员初始化功能已关闭"
            );

            return;
        }

        /*
         * 第一步：检查ADMIN角色。
         */
        SysRole adminRole =
                sysRoleMapper.findByCode(
                        "ADMIN"
                );

        if (adminRole == null) {
            throw new IllegalStateException(
                    "ADMIN角色不存在，请先执行rbac_ddl.sql"
            );
        }

        if (!"ENABLED".equals(adminRole.getStatus())) {
            throw new IllegalStateException(
                    "ADMIN角色已被禁用，无法初始化默认管理员"
            );
        }

        /*
         * 第二步：根据用户名查询默认管理员。
         */
        SysUser adminUser =
                sysUserMapper.findByUsername(
                        adminUsername
                );

        /*
         * 第三步：管理员不存在时创建。
         */
        if (adminUser == null) {
            createDefaultAdmin();

            adminUser =
                    sysUserMapper.findByUsername(
                            adminUsername
                    );

            if (adminUser == null) {
                throw new IllegalStateException(
                        "默认管理员创建后查询失败"
                );
            }

            LOGGER.info(
                    "默认管理员创建成功，username："
                            + adminUsername
            );

        } else {
            LOGGER.info(
                    "默认管理员已经存在，username："
                            + adminUsername
            );
        }

        /*
         * 第四步：确保默认管理员拥有ADMIN角色。
         *
         * assignRole使用INSERT IGNORE，
         * 已经存在该关系时不会重复插入。
         */
        sysUserRoleMapper.assignRole(
                adminUser.getId(),
                adminRole.getId()
        );

        LOGGER.info(
                "默认管理员ADMIN角色检查完成，userId："
                        + adminUser.getId()
        );

        /*
         * 已禁用的管理员不自动重新启用。
         * 避免每次重启都覆盖管理员主动设置的状态。
         */
        if (!"ENABLED".equals(adminUser.getStatus())) {
            LOGGER.info(
                    "警告：默认管理员当前已被禁用，userId："
                            + adminUser.getId()
            );
        }
    }

    private void createDefaultAdmin() {
        String normalizedAdminId =
                requireText(
                        adminId,
                        "默认管理员ID不能为空"
                );

        String normalizedUsername =
                requireText(
                        adminUsername,
                        "默认管理员用户名不能为空"
                );

        /*
         * 只有真正需要创建管理员时，
         * 才要求配置初始密码。
         */
        String normalizedPassword =
                requireText(
                        adminPassword,
                        "默认管理员不存在，请通过"
                                + "ADMIN_BOOTSTRAP_PASSWORD"
                                + "环境变量设置初始密码"
                );

        /*
         * 防止管理员ID已经被其他用户占用。
         */
        SysUser sameIdUser =
                sysUserMapper.selectById(
                        normalizedAdminId
                );

        if (sameIdUser != null) {
            throw new IllegalStateException(
                    "默认管理员ID已经被其他用户占用："
                            + normalizedAdminId
            );
        }

        SysUser newAdmin =
                new SysUser();

        newAdmin.setId(
                normalizedAdminId
        );

        newAdmin.setUsername(
                normalizedUsername
        );

        newAdmin.setPassword(
                PasswordUtil.hash(
                        normalizedPassword
                )
        );

        newAdmin.setStatus(
                "ENABLED"
        );

        int insertedRows =
                sysUserMapper.insert(
                        newAdmin
                );

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "创建默认管理员失败"
            );
        }
    }

    private String requireText(
            String value,
            String message
    ) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    message
            );
        }

        return value.trim();
    }
}
