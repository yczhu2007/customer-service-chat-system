package com.example.customerservice.controller;

import com.example.customerservice.constant.PermissionCodes;

import com.example.customerservice.constant.RoleCodes;

import com.example.customerservice.dto.*;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import com.example.customerservice.common.Result;

/**
 * 角色和权限管理接口。
 */
@RestController
@Validated
public class RoleController {

    @Autowired
    private IRoleService roleService;

    @Autowired
    private CurrentUser currentUser;

    @GetMapping("/roles")
    public Result<PageResult<RoleVO>> findAllRoles(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNo不能小于1")
            long pageNo,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize不能小于1")
            @Max(value = 100, message = "pageSize不能大于100")
            long pageSize
    ) {
        requireRoleManagementPermission();
        return Result.success(roleService.findRolePage(pageNo, pageSize));
    }

    @GetMapping("/roles/{id}")
    public Result<RoleVO> findRoleById(
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String id
    ) {
        requireRoleManagementPermission();
        return Result.success(roleService.findRoleById(id));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RoleVO> createRole(
            @Valid
            @RequestBody RoleCreateDTO request
    ) {
        requireRoleManagementPermission();
        return Result.success(
                HttpStatus.CREATED.value(),
                "角色创建成功",
                roleService.createRole(request)
        );
    }

    @PutMapping("/roles/{id}")
    public Result<RoleVO> updateRole(
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String id,
            @Valid
            @RequestBody RoleUpdateDTO request
    ) {
        requireRoleManagementPermission();
        return Result.success(
                roleService.updateRole(id, request)
        );
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String id
    ) {
        requireRoleManagementPermission();

        roleService.deleteRole(id);

        return Result.successMessage(
                "角色删除成功"
        );
    }

    @GetMapping("/permissions")
    public Result<PageResult<PermissionVO>> findAllPermissions(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNo不能小于1")
            long pageNo,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize不能小于1")
            @Max(value = 100, message = "pageSize不能大于100")
            long pageSize
    ) {
        requirePermissionManagementPermission();
        return Result.success(roleService.findPermissionPage(pageNo, pageSize));
    }

    @GetMapping("/permissions/{id}")
    public Result<PermissionVO> findPermissionById(
            @PathVariable
            @NotBlank(message = "权限ID不能为空")
            @Size(max = 64, message = "权限ID长度不能超过64个字符")
            String id
    ) {
        requirePermissionManagementPermission();
        return Result.success(roleService.findPermissionById(id));
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PermissionVO> createPermission(
            @Valid
            @RequestBody
            PermissionCreateDTO request
    ) {
        requirePermissionManagementPermission();

        return Result.success(
                HttpStatus.CREATED.value(),
                "权限创建成功",
                roleService.createPermission(request)
        );
    }

    @PutMapping("/permissions/{id}")
    public Result<PermissionVO> updatePermission(
            @PathVariable
            @NotBlank(message = "权限ID不能为空")
            @Size(max = 64, message = "权限ID长度不能超过64个字符")
            String id,
            @Valid
            @RequestBody
            PermissionUpdateDTO request
    ) {
        requirePermissionManagementPermission();

        return Result.success(
                roleService.updatePermission(id, request)
        );
    }

    @DeleteMapping("/permissions/{id}")
    public Result<Void> deletePermission(
            @PathVariable
            @NotBlank(message = "权限ID不能为空")
            @Size(max = 64, message = "权限ID长度不能超过64个字符")
            String id
    ) {
        requirePermissionManagementPermission();

        roleService.deletePermission(id);

        return Result.successMessage(
                "权限删除成功"
        );
    }

    @GetMapping("/roles/{roleId}/permissions")
    public Result<Set<String>> findRolePermissions(
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String roleId
    ) {
        requireRolePermissionManagementPermission();

        return Result.success(
                roleService.findPermissionCodesByRoleId(roleId)
        );
    }

    @PutMapping(
            "/roles/{roleId}/permissions/{permissionId}"
    )
    public Result<Void> assignPermissionToRole(
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String roleId,
            @PathVariable
            @NotBlank(message = "权限ID不能为空")
            @Size(max = 64, message = "权限ID长度不能超过64个字符")
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
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String roleId,
            @PathVariable
            @NotBlank(message = "权限ID不能为空")
            @Size(max = 64, message = "权限ID长度不能超过64个字符")
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
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.ROLE_MANAGE);
    }

    private void requirePermissionManagementPermission() {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(
                PermissionCodes.PERMISSION_MANAGE
        );
    }
    private void requireRolePermissionManagementPermission() {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.ROLE_MANAGE);
        currentUser.requirePermission(
                PermissionCodes.PERMISSION_MANAGE
        );
    }
}
