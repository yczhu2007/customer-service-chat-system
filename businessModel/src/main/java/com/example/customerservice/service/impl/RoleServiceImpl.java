package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.domain.SysPermission;
import com.example.customerservice.domain.SysRole;
import com.example.customerservice.dto.*;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.SysPermissionMapper;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysRolePermissionMapper;
import com.example.customerservice.service.IRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 角色与权限管理实现。
 */
@Service
public class RoleServiceImpl extends RbacServiceSupport implements IRoleService {

    private final SysRoleMapper sysRoleMapper;

    private final SysPermissionMapper sysPermissionMapper;

    private final SysRolePermissionMapper sysRolePermissionMapper;

    public RoleServiceImpl(
            SysRoleMapper sysRoleMapper,
            SysPermissionMapper sysPermissionMapper,
            SysRolePermissionMapper sysRolePermissionMapper
    ) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
    }

    @Override
    public List<RoleVO> findAllRoles() {
        return sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .orderByAsc(SysRole::getCreateTime)
                )
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    @Override
    public RoleVO findRoleById(String id) {
        return toRoleVO(requireRole(id));
    }

    @Override
    @Transactional
    public RoleVO createRole(RoleCreateDTO request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "创建角色请求不能为空"
            );
        }

        String roleCode = requireText(
                request.getRoleCode(),
                "roleCode不能为空"
        ).toUpperCase(Locale.ROOT);

        String roleName = requireText(
                request.getRoleName(),
                "roleName不能为空"
        );

        if (sysRoleMapper.findByCode(roleCode) != null) {
            throw new IllegalArgumentException(
                    "角色编码已经存在"
            );
        }

        String id = StringUtils.hasText(request.getId())
                ? request.getId().trim()
                : UUID.randomUUID()
                .toString()
                .replace("-", "");

        if (sysRoleMapper.selectById(id) != null) {
            throw new IllegalArgumentException(
                    "角色ID已经存在"
            );
        }

        SysRole role = new SysRole();

        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(request.getDescription());
        role.setStatus(normalizeStatus(
                request.getStatus(),
                "ENABLED"
        ));

        int insertedRows = sysRoleMapper.insert(role);

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "创建角色失败"
            );
        }

        return findRoleById(id);
    }

    @Override
    @Transactional
    public RoleVO updateRole(
            String id,
            RoleUpdateDTO request
    ) {
        SysRole currentRole = requireRole(id);

        if (request == null) {
            throw new IllegalArgumentException(
                    "修改角色请求不能为空"
            );
        }

        boolean hasRoleCode =
                StringUtils.hasText(request.getRoleCode());

        boolean hasRoleName =
                StringUtils.hasText(request.getRoleName());

        boolean hasDescription =
                request.getDescription() != null;

        boolean hasStatus =
                StringUtils.hasText(request.getStatus());

        if (
                !hasRoleCode &&
                        !hasRoleName &&
                        !hasDescription &&
                        !hasStatus
        ) {
            throw new IllegalArgumentException(
                    "至少需要提供一个修改字段"
            );
        }

        SysRole role = new SysRole();
        role.setId(currentRole.getId());

        if (hasRoleCode) {
            String roleCode = request.getRoleCode()
                    .trim()
                    .toUpperCase(Locale.ROOT);

            SysRole sameCodeRole =
                    sysRoleMapper.findByCode(roleCode);

            if (
                    sameCodeRole != null &&
                            !currentRole.getId()
                                    .equals(sameCodeRole.getId())
            ) {
                throw new IllegalArgumentException(
                        "角色编码已经存在"
                );
            }

            role.setRoleCode(roleCode);
        }

        if (hasRoleName) {
            role.setRoleName(
                    request.getRoleName().trim()
            );
        }

        if (hasDescription) {
            role.setDescription(
                    request.getDescription()
            );
        }

        if (hasStatus) {
            role.setStatus(normalizeStatus(
                    request.getStatus(),
                    currentRole.getStatus()
            ));
        }

        sysRoleMapper.updateSelective(role);

        return findRoleById(currentRole.getId());
    }

    @Override
    @Transactional
    public void deleteRole(String id) {
        SysRole role = requireRole(id);

        if (
                "ADMIN".equals(role.getRoleCode()) ||
                        "AGENT".equals(role.getRoleCode()) ||
                        "USER".equals(role.getRoleCode())
        ) {
            throw new IllegalArgumentException(
                    "系统基础角色不允许删除"
            );
        }

        /*
         * 先删除角色权限关系。
         * 用户角色关系由数据库外键级联删除。
         */
        sysRolePermissionMapper.deleteByRoleId(
                role.getId()
        );

        int deletedRows =
                sysRoleMapper.deleteById(role.getId());

        if (deletedRows != 1) {
            throw new IllegalStateException(
                    "删除角色失败"
            );
        }
    }

    @Override
    public List<PermissionVO> findAllPermissions() {
        return sysPermissionMapper.selectList(
                        Wrappers.<SysPermission>lambdaQuery()
                                .orderByAsc(SysPermission::getCreateTime)
                )
                .stream()
                .map(this::toPermissionVO)
                .toList();
    }

    @Override
    public PermissionVO findPermissionById(String id) {
        return toPermissionVO(
                requirePermission(id)
        );
    }

    @Override
    @Transactional
    public PermissionVO createPermission(
            PermissionCreateDTO request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "创建权限请求不能为空"
            );
        }

        String permissionCode = requireText(
                request.getPermissionCode(),
                "permissionCode不能为空"
        );

        String permissionName = requireText(
                request.getPermissionName(),
                "permissionName不能为空"
        );

        if (
                sysPermissionMapper.findByCode(
                        permissionCode
                ) != null
        ) {
            throw new IllegalArgumentException(
                    "权限编码已经存在"
            );
        }

        String id = StringUtils.hasText(request.getId())
                ? request.getId().trim()
                : UUID.randomUUID()
                .toString()
                .replace("-", "");

        if (sysPermissionMapper.selectById(id) != null) {
            throw new IllegalArgumentException(
                    "权限ID已经存在"
            );
        }

        SysPermission permission =
                new SysPermission();

        permission.setId(id);
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(permissionName);
        permission.setPermissionType(
                normalizePermissionType(
                        request.getPermissionType(),
                        "API"
                )
        );
        permission.setRequestMethod(
                normalizeRequestMethod(
                        request.getRequestMethod()
                )
        );
        permission.setRequestPath(
                normalizeNullableText(
                        request.getRequestPath()
                )
        );
        permission.setDescription(
                request.getDescription()
        );
        permission.setStatus(
                normalizeStatus(
                        request.getStatus(),
                        "ENABLED"
                )
        );

        int insertedRows =
                sysPermissionMapper.insert(permission);

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "创建权限失败"
            );
        }

        return findPermissionById(id);
    }

    @Override
    @Transactional
    public PermissionVO updatePermission(
            String id,
            PermissionUpdateDTO request
    ) {
        SysPermission currentPermission =
                requirePermission(id);

        if (request == null) {
            throw new IllegalArgumentException(
                    "修改权限请求不能为空"
            );
        }

        boolean hasPermissionCode =
                StringUtils.hasText(
                        request.getPermissionCode()
                );

        boolean hasPermissionName =
                StringUtils.hasText(
                        request.getPermissionName()
                );

        boolean hasPermissionType =
                StringUtils.hasText(
                        request.getPermissionType()
                );

        boolean hasRequestMethod =
                request.getRequestMethod() != null;

        boolean hasRequestPath =
                request.getRequestPath() != null;

        boolean hasDescription =
                request.getDescription() != null;

        boolean hasStatus =
                StringUtils.hasText(
                        request.getStatus()
                );

        if (
                !hasPermissionCode &&
                        !hasPermissionName &&
                        !hasPermissionType &&
                        !hasRequestMethod &&
                        !hasRequestPath &&
                        !hasDescription &&
                        !hasStatus
        ) {
            throw new IllegalArgumentException(
                    "至少需要提供一个修改字段"
            );
        }

        SysPermission permission =
                new SysPermission();

        permission.setId(currentPermission.getId());

        if (
                hasPermissionCode
        ) {
            String permissionCode =
                    request.getPermissionCode().trim();

            SysPermission sameCodePermission =
                    sysPermissionMapper.findByCode(
                            permissionCode
                    );

            if (
                    sameCodePermission != null &&
                            !currentPermission.getId()
                                    .equals(
                                            sameCodePermission.getId()
                                    )
            ) {
                throw new IllegalArgumentException(
                        "权限编码已经存在"
                );
            }

            permission.setPermissionCode(
                    permissionCode
            );
        }

        if (
                hasPermissionName
        ) {
            permission.setPermissionName(
                    request.getPermissionName().trim()
            );
        }

        if (
                hasPermissionType
        ) {
            permission.setPermissionType(
                    normalizePermissionType(
                            request.getPermissionType(),
                            currentPermission
                                    .getPermissionType()
                    )
            );
        }

        if (hasRequestMethod) {
            permission.setRequestMethod(
                    request.getRequestMethod()
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );
        }

        if (hasRequestPath) {
            permission.setRequestPath(
                    request.getRequestPath().trim()
            );
        }

        if (hasDescription) {
            permission.setDescription(
                    request.getDescription()
            );
        }

        if (
                hasStatus
        ) {
            permission.setStatus(
                    normalizeStatus(
                            request.getStatus(),
                            currentPermission.getStatus()
                    )
            );
        }

        sysPermissionMapper.updateSelective(permission);

        return findPermissionById(
                currentPermission.getId()
        );
    }

    @Override
    @Transactional
    public void deletePermission(String id) {
        SysPermission permission =
                requirePermission(id);

        /*
         * 当前基础权限正在被聊天和管理功能使用，
         * 因此禁止直接删除。
         */
        if (
                permission.getPermissionCode()
                        .startsWith("chat:") ||
                        permission.getPermissionCode()
                                .endsWith(":manage")
        ) {
            throw new IllegalArgumentException(
                    "系统基础权限不允许删除"
            );
        }

        int deletedRows =
                sysPermissionMapper.deleteById(
                        permission.getId()
                );

        if (deletedRows != 1) {
            throw new IllegalStateException(
                    "删除权限失败"
            );
        }
    }
    @Override
    public Set<String> findPermissionCodesByRoleId(
            String roleId
    ) {
        SysRole role = requireRole(roleId);

        Set<String> permissionCodes =
                sysRolePermissionMapper
                        .findAllPermissionCodesByRoleId(
                                role.getId()
                        );

        return permissionCodes == null
                ? Set.of()
                : permissionCodes;
    }

    @Override
    @Transactional
    public void assignPermissionToRole(
            String roleId,
            String permissionId
    ) {
        SysRole role = requireRole(roleId);

        SysPermission permission =
                requirePermission(permissionId);

        if (!"ENABLED".equals(role.getStatus())) {
            throw new IllegalArgumentException(
                    "不能给已禁用角色分配权限"
            );
        }

        if (!"ENABLED".equals(permission.getStatus())) {
            throw new IllegalArgumentException(
                    "不能分配已禁用权限"
            );
        }

        /*
         * Mapper使用INSERT IGNORE，
         * 重复分配不会产生重复数据。
         */
        sysRolePermissionMapper.assignPermission(
                role.getId(),
                permission.getId()
        );
    }

    @Override
    @Transactional
    public void removePermissionFromRole(
            String roleId,
            String permissionId
    ) {
        SysRole role = requireRole(roleId);

        SysPermission permission =
                requirePermission(permissionId);

        /*
         * 防止移除ADMIN的核心管理权限后，
         * 所有管理员都无法继续管理系统。
         */
        if (
                "ADMIN".equals(role.getRoleCode()) &&
                        (
                                "user:manage".equals(
                                        permission.getPermissionCode()
                                ) ||
                                        "role:manage".equals(
                                                permission.getPermissionCode()
                                        ) ||
                                        "permission:manage".equals(
                                                permission.getPermissionCode()
                                        )
                        )
        ) {
            throw new IllegalArgumentException(
                    "不能移除ADMIN角色的核心管理权限"
            );
        }

        sysRolePermissionMapper.removePermission(
                role.getId(),
                permission.getId()
        );
    }
    private SysRole requireRole(String id) {
        String normalizedId = requireText(
                id,
                "角色ID不能为空"
        );

        SysRole role =
                sysRoleMapper.selectById(normalizedId);

        if (role == null) {
            throw new NotFoundException(
                    "角色不存在"
            );
        }

        return role;
    }

    private SysPermission requirePermission(String id) {
        String normalizedId = requireText(
                id,
                "权限ID不能为空"
        );

        SysPermission permission =
                sysPermissionMapper.selectById(
                        normalizedId
                );

        if (permission == null) {
            throw new NotFoundException(
                    "权限不存在"
            );
        }

        return permission;
    }

    private RoleVO toRoleVO(SysRole role) {
        RoleVO roleVO = new RoleVO();

        roleVO.setId(role.getId());
        roleVO.setRoleCode(role.getRoleCode());
        roleVO.setRoleName(role.getRoleName());
        roleVO.setDescription(role.getDescription());
        roleVO.setStatus(role.getStatus());
        roleVO.setCreateTime(role.getCreateTime());
        roleVO.setUpdateTime(role.getUpdateTime());

        Set<String> permissions =
                sysRolePermissionMapper
                        .findAllPermissionCodesByRoleId(
                                role.getId()
                        );

        roleVO.setPermissions(
                permissions == null
                        ? Set.of()
                        : permissions
        );

        return roleVO;
    }






}
