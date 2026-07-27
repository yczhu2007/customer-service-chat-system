package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SysRole;
import org.apache.ibatis.annotations.Param;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    SysRole findByCode(
            @Param("roleCode")
            String roleCode
    );


    int updateSelective(
            SysRole role
    );
}
