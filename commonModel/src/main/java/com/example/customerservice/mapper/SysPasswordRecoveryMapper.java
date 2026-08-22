package com.example.customerservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.customerservice.domain.SysPasswordRecovery;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface SysPasswordRecoveryMapper extends BaseMapper<SysPasswordRecovery> {

    /**
     * Atomically consumes the exact, still-valid recovery-code hash.
     * A successful call prevents concurrent requests from using the same code.
     */
    int consumeRecoveryCode(
            @Param("userId") String userId,
            @Param("recoveryHash") String recoveryHash,
            @Param("now") LocalDateTime now
    );
}
