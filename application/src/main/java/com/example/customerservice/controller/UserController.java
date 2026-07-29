package com.example.customerservice.controller;

import com.example.customerservice.dto.PasswordUpdateDTO;
import com.example.customerservice.dto.UserCreateDTO;
import com.example.customerservice.dto.UserUpdateDTO;
import com.example.customerservice.dto.UserVO;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import jakarta.validation.Valid;
import com.example.customerservice.common.Result;

/**
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private CurrentUser currentUser;


    @GetMapping
    public Result<List<UserVO>> findAll() {

        requireUserManagementPermission();

        return Result.success(userService.findAll());
    }


    @GetMapping("/{id}")
    public Result<UserVO> findById(
            @PathVariable
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


    @PutMapping("/{id}/password")
    public Result<Void> updatePassword(
            @PathVariable
            String id,
            @Valid
            @RequestBody
            PasswordUpdateDTO request
    ) {
        requireUserManagementPermission();

        userService.updatePassword(
                id,
                request
        );

        return Result.successMessage(
                "密码修改成功"
        );
    }


    @DeleteMapping("/{id}")
    public Result<Void> deleteById(
            @PathVariable
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
            String userId,
            @PathVariable
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
            String userId,
            @PathVariable
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
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("user:manage");
        currentUser.requirePermission("role:manage");
    }
}
