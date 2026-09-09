package com.example.customerservice.controller;

import com.example.customerservice.constant.PermissionCodes;

import com.example.customerservice.constant.RoleCodes;

import com.example.customerservice.dto.UserCreateDTO;
import com.example.customerservice.dto.UserUpdateDTO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import com.example.customerservice.common.Result;

/**
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private CurrentUser currentUser;


    @GetMapping
    public Result<PageResult<UserVO>> findAll(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "pageNo不能小于1")
            long pageNo,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize不能小于1")
            @Max(value = 100, message = "pageSize不能大于100")
            long pageSize,
            @RequestParam(required = false)
            @Size(max = 64, message = "查询关键词长度不能超过64个字符")
            String keyword
    ) {

        requireUserManagementPermission();

        return Result.success(userService.findPage(pageNo, pageSize, keyword));
    }


    @GetMapping("/{id}")
    public Result<UserVO> findById(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String id
    ) {

        requireUserManagementPermission();

        return Result.success(userService.findById(id));
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserVO> create(
            @Valid
            @RequestBody
            UserCreateDTO request
    ) {

        requireUserManagementPermission();

        return Result.success(
                HttpStatus.CREATED.value(),
                "用户创建成功",
                userService.create(request)
        );
    }


    @PutMapping("/{id}")
    public Result<UserVO> update(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String id,
            @Valid
            @RequestBody
            UserUpdateDTO request
    ) {

        requireUserManagementPermission();

        return Result.success(
                userService.update(id, request)
        );
    }


    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String id
    ) {
        requireUserManagementPermission();

        userService.resetPasswordByAdmin(id);

        return Result.successMessage(
                "密码已重置为12345678"
        );
    }


    @DeleteMapping("/{id}")
    public Result<Void> deleteById(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String id
    ) {
        requireUserManagementPermission();

        userService.deleteById(
                id
        );

        return Result.successMessage(
                "用户删除成功"
        );
    }
    @GetMapping("/{userId}/roles")
    public Result<Set<String>> findUserRoles(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String userId
    ) {
        requireUserRoleManagementPermission();

        return Result.success(
                userService.findRoleCodesByUserId(userId)
        );
    }

    @PutMapping("/{userId}/roles/{roleId}")
    public Result<Void> assignRoleToUser(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String userId,
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String roleId
    ) {
        requireUserRoleManagementPermission();

        userService.assignRoleToUser(
                userId,
                roleId
        );

        return Result.successMessage(
                "用户角色分配成功"
        );
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    public Result<Void> removeRoleFromUser(
            @PathVariable
            @NotBlank(message = "用户ID不能为空")
            @Size(max = 64, message = "用户ID长度不能超过64个字符")
            String userId,
            @PathVariable
            @NotBlank(message = "角色ID不能为空")
            @Size(max = 64, message = "角色ID长度不能超过64个字符")
            String roleId
    ) {
        requireUserRoleManagementPermission();

        userService.removeRoleFromUser(
                userId,
                roleId
        );

        return Result.successMessage(
                "用户角色移除成功"
        );
    }

    private void requireUserManagementPermission() {

        currentUser.requireRole(
                "ADMIN"
        );


        currentUser.requirePermission(
                "user:manage"
        );
    }

    private void requireUserRoleManagementPermission() {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.USER_MANAGE);
        currentUser.requirePermission(PermissionCodes.ROLE_MANAGE);
    }
}
