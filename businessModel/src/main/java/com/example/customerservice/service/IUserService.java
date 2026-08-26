package com.example.customerservice.service;

import com.example.customerservice.dto.UserCreateDTO;
import com.example.customerservice.dto.UserUpdateDTO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.dto.PageResult;

import java.util.Set;


/**
 * 系统用户管理服务。
 */
public interface IUserService {

    PageResult<UserVO> findPage(long pageNo, long pageSize, String keyword);

    UserVO findById(
            String id
    );

    UserVO create(
            UserCreateDTO request
    );

    UserVO update(
            String id,
            UserUpdateDTO request
    );

    void resetPasswordByAdmin(String id);

    void deleteById(
            String id
    );

    Set<String> findRoleCodesByUserId(
            String userId
    );

    void assignRoleToUser(
            String userId,
            String roleId
    );

    void removeRoleFromUser(
            String userId,
            String roleId
    );
}
