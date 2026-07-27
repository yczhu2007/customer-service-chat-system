package com.example.customerservice.controller;

import com.example.customerservice.dto.*;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IRoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import com.example.customerservice.common.Result;

/**
 * 角色和权限管理接口。
 *
 * 按照技术文档要求，
 * 角色与权限CRUD统一由RoleController提供。
 */
@RestController
public class RoleController {

    private final IRoleService roleService;

    private final CurrentUser currentUser;

    public RoleController(
            IRoleService roleService,
            CurrentUser currentUser
    ) {
        this.roleService = roleService;
        this.currentUser = currentUser;
    }

    @GetMapping("/roles")
    public List<RoleVO> findAllRoles() {
        requireRoleManagementPermission();
        return roleService.findAllRoles();
    }

    @GetMapping("/roles/{id}")
    public RoleVO findRoleById(
            @PathVariable String id
    ) {
        requireRoleManagementPermission();
        return roleService.findRoleById(id);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleVO createRole(
            @Valid
            @RequestBody RoleCreateDTO request
    ) {
        requireRoleManagementPermission();
        return roleService.createRole(request);
    }

    @PutMapping("/roles/{id}")
    public RoleVO updateRole(
            @PathVariable String id,
            @Valid
            @RequestBody RoleUpdateDTO request
    ) {
        requireRoleManagementPermission();
        return roleService.updateRole(id, request);
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(
            @PathVariable
            String id
    ) {
        requireRoleManagementPermission();

        roleService.deleteRole(id);

        return Result.successMessage(
                "角色删除成功"
        );
    }

    @GetMapping("/permissions")
    public List<PermissionVO> findAllPermissions() {
        requirePermissionManagementPermission();
        return roleService.findAllPermissions();
    }

    @GetMapping("/permissions/{id}")
    public PermissionVO findPermissionById(
            @PathVariable String id
    ) {
        requirePermissionManagementPermission();
        return roleService.findPermissionById(id);
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionVO createPermission(
            @Valid
            @RequestBody
            PermissionCreateDTO request
    ) {
        requirePermissionManagementPermission();

        return roleService.createPermission(
                request
        );
    }

    @PutMapping("/permissions/{id}")
    public PermissionVO updatePermission(
            @PathVariable
            String id,
            @Valid
            @RequestBody
            PermissionUpdateDTO request
    ) {
        requirePermissionManagementPermission();

        return roleService.updatePermission(
                id,
                request
        );
    }

    @DeleteMapping("/permissions/{id}")
    public Result<Void> deletePermission(
            @PathVariable
            String id
    ) {
        requirePermissionManagementPermission();

        roleService.deletePermission(id);

        return Result.successMessage(
                "权限删除成功"
        );
    }

    @GetMapping("/roles/{roleId}/permissions")
    public Set<String> findRolePermissions(
            @PathVariable
            String roleId
    ) {
        requireRolePermissionManagementPermission();

        return roleService
                .findPermissionCodesByRoleId(
                        roleId
                );
    }

    @PutMapping(
            "/roles/{roleId}/permissions/{permissionId}"
    )
    public Result<Void> assignPermissionToRole(
            @PathVariable
            String roleId,
            @PathVariable
            String permissionId
    ) {
        requireRolePermissionManagementPermission();

        roleService.assignPermissionToRole(
                roleId,
                permissionId
        );

        return Result.successMessage(
                "角色权限分配成功"
        );
    }

    @DeleteMapping(
            "/roles/{roleId}/permissions/{permissionId}"
    )
    public Result<Void> removePermissionFromRole(
            @PathVariable
            String roleId,
            @PathVariable
            String permissionId
    ) {
        requireRolePermissionManagementPermission();

        roleService.removePermissionFromRole(
                roleId,
                permissionId
        );

        return Result.successMessage(
                "角色权限移除成功"
        );
    }

    private void requireRoleManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("role:manage");
    }

    private void requirePermissionManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission(
                "permission:manage"
        );
    }
    private void requireRolePermissionManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("role:manage");
        currentUser.requirePermission(
                "permission:manage"
        );
    }
}