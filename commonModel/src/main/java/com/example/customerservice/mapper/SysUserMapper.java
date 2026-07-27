package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SysUser;
import org.apache.ibatis.annotations.Param;

public interface SysUserMapper extends BaseMapper<SysUser> {

    SysUser findByUsername(
            @Param("username")
            String username
    );


    int updateSelective(
            SysUser user
    );


    int updateStatus(
            @Param("id")
            String id,

            @Param("status")
            String status
    );


    int updatePassword(
            @Param("id")
            String id,

            @Param("password")
            String password
    );

}
