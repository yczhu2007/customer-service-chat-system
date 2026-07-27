package com.example.customerservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

@Mapper
public interface SysRolePermissionMapper {

    Set<String> findPermissionCodesByUserId(
            @Param("userId")
            String userId
    );


    Set<String> findPermissionCodesByRoleId(
            @Param("roleId")
            String roleId
    );

    Set<String> findAllPermissionCodesByRoleId(
            @Param("roleId")
            String roleId
    );


    int assignPermission(
            @Param("roleId")
            String roleId,

            @Param("permissionId")
            String permissionId
    );


    int removePermission(
            @Param("roleId")
            String roleId,

            @Param("permissionId")
            String permissionId
    );


    int deleteByRoleId(
            @Param("roleId")
            String roleId
    );
}
