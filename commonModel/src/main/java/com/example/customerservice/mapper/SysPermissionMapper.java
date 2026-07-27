package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SysPermission;
import org.apache.ibatis.annotations.Param;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    SysPermission findByCode(
            @Param("permissionCode")
            String permissionCode
    );


    int updateSelective(
            SysPermission permission
    );
}
