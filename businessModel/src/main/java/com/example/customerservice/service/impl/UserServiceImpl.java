package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.domain.SysRole;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.PasswordUpdateDTO;
import com.example.customerservice.dto.UserCreateDTO;
import com.example.customerservice.dto.UserUpdateDTO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.dto.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.IUserService;
import com.example.customerservice.service.TokenService;
import com.example.customerservice.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


/**
 * 基于sys_user表的用户管理服务。
 */
@Service
public class UserServiceImpl
        implements IUserService {

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;
    private final TokenService tokenService;

    @Value("${app.bootstrap.admin.id:admin}")
    private String bootstrapAdminId;

    @Value("${app.bootstrap.admin.username:admin}")
    private String bootstrapAdminUsername;


    public UserServiceImpl(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            TokenService tokenService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.tokenService = tokenService;
    }


    @Override
    public PageResult<UserVO> findPage(long pageNo, long pageSize) {
        Page<SysUser> page = sysUserMapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<SysUser>lambdaQuery()
                        .orderByDesc(SysUser::getCreateTime)
                        .orderByDesc(SysUser::getId)
        );
        List<UserVO> records = page.getRecords()
                .stream()
                .map(this::toUserVO)
                .toList();
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                records
        );
    }


    @Override
    public UserVO findById(
            String id
    ) {

        return toUserVO(
                requireUser(
                        id
                )
        );
    }


    @Override
    @Transactional
    public UserVO create(
            UserCreateDTO request
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "创建用户请求不能为空"
            );
        }


        String username =
                requireText(
                        request.getUsername(),
                        "username不能为空"
                );


        String rawPassword =
                requireText(
                        request.getPassword(),
                        "password不能为空"
                );


        if (
                sysUserMapper.findByUsername(
                        username
                ) != null
        ) {

            throw new IllegalArgumentException(
                    "用户名已经存在"
            );
        }


        String id =
                StringUtils.hasText(
                        request.getId()
                )
                        ? request.getId().trim()
                        : UUID.randomUUID()
                        .toString()
                        .replace(
                                "-",
                                ""
                        );


        if (
                sysUserMapper.selectById(
                        id
                ) != null
        ) {

            throw new IllegalArgumentException(
                    "用户ID已经存在"
            );
        }


        SysUser user =
                new SysUser();


        user.setId(
                id
        );


        user.setUsername(
                username
        );


        user.setPassword(
                PasswordUtil.hash(
                        rawPassword
                )
        );


        user.setStatus(
                normalizeStatus(
                        request.getStatus(),
                        "ENABLED"
                )
        );

        user.setVipLevel(
                normalizeVipLevel(
                        request.getVipLevel(),
                        0
                )
        );


        int insertedRows =
                sysUserMapper.insert(
                        user
                );


        if (insertedRows != 1) {

            throw new IllegalStateException(
                    "创建用户失败"
            );
        }


        return findById(
                id
        );
    }


    @Override
    @Transactional
    public UserVO update(
            String id,
            UserUpdateDTO request
    ) {

        SysUser currentUser =
                requireUser(
                        id
                );


        if (request == null) {

            throw new IllegalArgumentException(
                    "修改用户请求不能为空"
            );
        }


        boolean hasUsername =
                StringUtils.hasText(
                        request.getUsername()
                );


        boolean hasStatus =
                StringUtils.hasText(
                        request.getStatus()
                );

        boolean hasVipLevel =
                request.getVipLevel() != null;

        if (!hasUsername && !hasStatus && !hasVipLevel) {

            throw new IllegalArgumentException(
                    "username、status和vipLevel至少需要提供一项"
            );
        }


        SysUser user =
                new SysUser();


        user.setId(
                currentUser.getId()
        );


        if (hasUsername) {

            String username =
                    request.getUsername()
                            .trim();


            SysUser sameUsernameUser =
                    sysUserMapper.findByUsername(
                            username
                    );


            if (
                    sameUsernameUser != null &&
                            !currentUser.getId()
                                    .equals(
                                            sameUsernameUser.getId()
                                    )
            ) {

                throw new IllegalArgumentException(
                        "用户名已经存在"
                );
            }


            user.setUsername(
                    username
            );
        }


        if (hasStatus) {

            String normalizedStatus =
                    normalizeStatus(
                            request.getStatus(),
                            currentUser.getStatus()
                    );


            if (
                    isDefaultAdmin(
                            currentUser
                    ) &&
                            "DISABLED".equals(
                                    normalizedStatus
                            )
            ) {
                throw new IllegalArgumentException(
                        "不能禁用默认管理员"
                );
            }


            user.setStatus(
                    normalizedStatus
            );
        }

        if (hasVipLevel) {
            user.setVipLevel(
                    normalizeVipLevel(
                            request.getVipLevel(),
                            currentUser.getVipLevel() == null
                                    ? 0
                                    : currentUser.getVipLevel()
                    )
            );
        }


        sysUserMapper.updateSelective(
                user
        );

        if ("DISABLED".equals(user.getStatus())) {
            tokenService.revokeAllForUser(currentUser.getId());
        }


        return findById(
                currentUser.getId()
        );
    }


    @Override
    @Transactional
    public void updatePassword(
            String id,
            PasswordUpdateDTO request
    ) {

        requireUser(
                id
        );


        if (request == null) {

            throw new IllegalArgumentException(
                    "修改密码请求不能为空"
            );
        }


        String rawPassword =
                requireText(
                        request.getPassword(),
                        "password不能为空"
                );


        int updatedRows =
                sysUserMapper.updatePassword(
                        id,
                        PasswordUtil.hash(
                                rawPassword
                        )
                );


        if (updatedRows != 1) {

            throw new IllegalStateException(
                    "修改密码失败"
            );
        }

        tokenService.revokeAllForUser(id);
    }


    @Override
    @Transactional
    public void deleteById(
            String id
    ) {

        SysUser user =
                requireUser(
                        id
                );


        if (isDefaultAdmin(user)) {
            throw new IllegalArgumentException(
                    "不能删除默认管理员"
            );
        }


        int deletedRows =
                sysUserMapper.deleteById(
                        id
                );


        if (deletedRows != 1) {

            throw new IllegalStateException(
                    "删除用户失败"
            );
        }

        tokenService.revokeAllForUser(id);
    }

    @Override
    public Set<String> findRoleCodesByUserId(
            String userId
    ) {
        requireUser(userId);

        Set<String> roleCodes =
                sysUserRoleMapper
                        .findAllRoleCodesByUserId(userId);

        return roleCodes == null
                ? Set.of()
                : roleCodes;
    }

    @Override
    @Transactional
    public void assignRoleToUser(
            String userId,
            String roleId
    ) {
        requireUser(userId);

        SysRole role = requireRole(roleId);

        if (!"ENABLED".equals(role.getStatus())) {
            throw new IllegalArgumentException(
                    "不能分配已经禁用的角色"
            );
        }

        /*
         * Mapper使用INSERT IGNORE。
         * 已经拥有该角色时不会重复插入。
         */
        sysUserRoleMapper.assignRole(
                userId,
                role.getId()
        );
    }

    @Override
    @Transactional
    public void removeRoleFromUser(
            String userId,
            String roleId
    ) {
        SysUser user =
                requireUser(userId);

        SysRole role = requireRole(roleId);

        /*
         * 文档规定启动时创建默认管理员admin。
         * 禁止移除默认管理员的ADMIN角色，
         * 避免系统失去管理入口。
         */
        if (
                isDefaultAdmin(user) &&
                        "ADMIN".equals(role.getRoleCode())
        ) {
            throw new IllegalArgumentException(
                    "不能移除默认管理员的ADMIN角色"
            );
        }

        sysUserRoleMapper.removeRole(
                userId,
                role.getId()
        );
    }
    private SysUser requireUser(
            String id
    ) {

        String normalizedId =
                requireText(
                        id,
                        "用户ID不能为空"
                );


        SysUser user =
                sysUserMapper.selectById(
                        normalizedId
                );


        if (user == null) {

            throw new NotFoundException(
                    "用户不存在"
            );
        }


        return user;
    }
    private SysRole requireRole(
            String roleId
    ) {
        String normalizedRoleId =
                requireText(
                        roleId,
                        "角色ID不能为空"
                );

        SysRole role =
                sysRoleMapper.selectById(
                        normalizedRoleId
                );

        if (role == null) {
            throw new NotFoundException(
                    "角色不存在"
            );
        }

        return role;
    }

    private String requireText(
            String value,
            String message
    ) {

        if (
                !StringUtils.hasText(
                        value
                )
        ) {

            throw new IllegalArgumentException(
                    message
            );
        }


        return value.trim();
    }


    private String normalizeStatus(
            String status,
            String defaultStatus
    ) {

        if (
                !StringUtils.hasText(
                        status
                )
        ) {

            return defaultStatus;
        }


        String normalizedStatus =
                status.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                !"ENABLED".equals(
                        normalizedStatus
                ) &&
                        !"DISABLED".equals(
                                normalizedStatus
                        )
        ) {

            throw new IllegalArgumentException(
                    "status只能是ENABLED或DISABLED"
            );
        }


        return normalizedStatus;
    }


    private UserVO toUserVO(
            SysUser user
    ) {

        UserVO userVO =
                new UserVO();


        userVO.setId(
                user.getId()
        );


        userVO.setUsername(
                user.getUsername()
        );


        userVO.setStatus(
                user.getStatus()
        );

        userVO.setVipLevel(
                user.getVipLevel() == null
                        ? 0
                        : user.getVipLevel()
        );


        userVO.setCreateTime(
                user.getCreateTime()
        );


        userVO.setUpdateTime(
                user.getUpdateTime()
        );

        Set<String> roleCodes =
                sysUserRoleMapper
                        .findAllRoleCodesByUserId(
                                user.getId()
                        );

        userVO.setRoles(
                roleCodes == null
                        ? Set.of()
                        : roleCodes
        );
        return userVO;
    }

    private int normalizeVipLevel(
            Integer vipLevel,
            int defaultLevel
    ) {
        int normalizedLevel =
                vipLevel == null
                        ? defaultLevel
                        : vipLevel;
        if (normalizedLevel < 0 || normalizedLevel > 5) {
            throw new IllegalArgumentException(
                    "vipLevel必须在0到5之间"
            );
        }
        return normalizedLevel;
    }


    private boolean isDefaultAdmin(
            SysUser user
    ) {

        if (user == null) {
            return false;
        }

        return (
                StringUtils.hasText(
                        bootstrapAdminId
                ) &&
                        bootstrapAdminId.equals(
                                user.getId()
                        )
        ) || (
                StringUtils.hasText(
                        bootstrapAdminUsername
                ) &&
                        bootstrapAdminUsername.equals(
                                user.getUsername()
                        )
        );
    }
}
