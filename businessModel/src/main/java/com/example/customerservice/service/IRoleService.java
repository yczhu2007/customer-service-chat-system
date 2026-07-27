package com.example.customerservice.service;

import com.example.customerservice.dto.*;

import java.util.List;
import java.util.Set;

/**
 * 角色与权限管理服务。
 */
public interface IRoleService {

    List<RoleVO> findAllRoles();

    RoleVO findRoleById(String id);

    RoleVO createRole(RoleCreateDTO request);

    RoleVO updateRole(String id, RoleUpdateDTO request);

    void deleteRole(String id);

    List<PermissionVO> findAllPermissions();

    PermissionVO findPermissionById(String id);

    PermissionVO createPermission(PermissionCreateDTO request);

    PermissionVO updatePermission(
            String id,
            PermissionUpdateDTO request
    );

    void deletePermission(String id);

    Set<String> findPermissionCodesByRoleId(
            String roleId
    );

    void assignPermissionToRole(
            String roleId,
            String permissionId
    );

    void removePermissionFromRole(
            String roleId,
            String permissionId
    );
}