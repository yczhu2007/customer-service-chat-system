package com.example.customerservice.controller;

import com.example.customerservice.dto.PasswordUpdateDTO;
import com.example.customerservice.dto.UserCreateDTO;
import com.example.customerservice.dto.UserUpdateDTO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.IUserService;
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

    private final IUserService userService;

    private final CurrentUser currentUser;


    public UserController(
            IUserService userService,
            CurrentUser currentUser
    ) {

        this.userService =
                userService;

        this.currentUser =
                currentUser;
    }


    @GetMapping
    public List<UserVO> findAll() {

        requireUserManagementPermission();

        return userService.findAll();
    }


    @GetMapping("/{id}")
    public UserVO findById(
            @PathVariable
            String id
    ) {

        requireUserManagementPermission();

        return userService.findById(
                id
        );
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserVO create(
            @Valid
            @RequestBody
            UserCreateDTO request
    ) {

        requireUserManagementPermission();

        return userService.create(
                request
        );
    }


    @PutMapping("/{id}")
    public UserVO update(
            @PathVariable
            String id,
            @Valid
            @RequestBody
            UserUpdateDTO request
    ) {

        requireUserManagementPermission();

        return userService.update(
                id,
                request
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
    public Set<String> findUserRoles(
            @PathVariable
            String userId
    ) {
        requireUserRoleManagementPermission();

        return userService.findRoleCodesByUserId(
                userId
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
