package com.example.customerservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

@Mapper
public interface SysUserRoleMapper {

    Set<String> findRoleCodesByUserId(
            @Param("userId")
            String userId
    );

    Set<String> findAllRoleCodesByUserId(
            @Param("userId")
            String userId
    );


    int assignRole(
            @Param("userId")
            String userId,

            @Param("roleId")
            String roleId
    );


    int removeRole(
            @Param("userId")
            String userId,

            @Param("roleId")
            String roleId
    );

}
