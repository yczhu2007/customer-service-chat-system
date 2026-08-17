package com.example.customerservice.service.impl;

import com.example.customerservice.domain.SysRole;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.domain.SysPasswordRecovery;
import com.example.customerservice.dto.PasswordResetDTO;
import com.example.customerservice.dto.ProfilePasswordUpdateDTO;
import com.example.customerservice.dto.ProfileUpdateDTO;
import com.example.customerservice.dto.RecoveryCodeVO;
import com.example.customerservice.dto.UserRegisterDTO;
import com.example.customerservice.dto.UserRegistrationVO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.SysRoleMapper;
import com.example.customerservice.mapper.SysPasswordRecoveryMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.service.TokenService;
import com.example.customerservice.service.UserAccountService;
import com.example.customerservice.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import com.example.customerservice.exception.BusinessStateException;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAccountServiceImpl implements UserAccountService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPasswordRecoveryMapper passwordRecoveryMapper;
    private final TokenService tokenService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long recoveryCodeTtlDays;

    public UserAccountServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper,
                                  SysUserRoleMapper userRoleMapper,
                                  SysPasswordRecoveryMapper passwordRecoveryMapper,
                                  TokenService tokenService,
                                  @Value("${app.auth.recovery-code-ttl-days:90}") long recoveryCodeTtlDays) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordRecoveryMapper = passwordRecoveryMapper;
        this.tokenService = tokenService;
        this.recoveryCodeTtlDays = Math.max(1, Math.min(recoveryCodeTtlDays, 365));
    }

    @Override
    @Transactional
    public UserRegistrationVO register(UserRegisterDTO request) {
        String username = request.getUsername().trim();
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已经存在");
        }
        SysRole defaultRole = roleMapper.findByCode("USER");
        if (defaultRole == null || !"ENABLED".equals(defaultRole.getStatus())) {
            throw new IllegalStateException("系统默认USER角色未配置或已禁用");
        }
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(username);
        user.setPassword(PasswordUtil.hash(request.getPassword()));
        user.setStatus("ENABLED");
        user.setVipLevel(0);
        try {
            if (userMapper.insert(user) != 1 || userRoleMapper.assignRole(user.getId(), defaultRole.getId()) != 1) {
                throw new IllegalStateException("用户注册失败");
            }
            String recoveryCode = createAndStoreRecoveryCode(user.getId());
            return new UserRegistrationVO(toVO(requireUser(user.getId())), recoveryCode);
        } catch (DuplicateKeyException exception) {
            throw new BusinessStateException("用户名已经存在");
        }
    }

    @Override
    public UserVO getProfile(String userId) { return toVO(requireUser(userId)); }

    @Override
    @Transactional
    public UserVO updateProfile(String userId, ProfileUpdateDTO request) {
        SysUser user = requireUser(userId);
        String username = request.getUsername().trim();
        SysUser duplicate = userMapper.findByUsername(username);
        if (duplicate != null && !duplicate.getId().equals(userId)) {
            throw new IllegalArgumentException("用户名已经存在");
        }
        user.setUsername(username);
        if (userMapper.updateSelective(user) != 1) {
            throw new IllegalStateException("账号资料修改失败");
        }
        return toVO(requireUser(userId));
    }

    @Override
    @Transactional
    public void updatePassword(String userId, ProfilePasswordUpdateDTO request) {
        SysUser user = requireUser(userId);
        if (!PasswordUtil.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        if (PasswordUtil.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        if (userMapper.updatePassword(userId, PasswordUtil.hash(request.getNewPassword())) != 1) {
            throw new IllegalStateException("密码修改失败");
        }
        tokenService.revokeAllForUser(userId);
    }

    @Override
    @Transactional
    public RecoveryCodeVO regenerateRecoveryCode(String userId) {
        requireUser(userId);
        return new RecoveryCodeVO(createAndStoreRecoveryCode(userId));
    }

    @Override
    @Transactional
    public RecoveryCodeVO resetPassword(PasswordResetDTO request) {
        SysUser user = userMapper.findByUsername(request.getUsername().trim());
        SysPasswordRecovery recovery = user == null
                ? null : passwordRecoveryMapper.selectById(user.getId());
        String submittedCode = request.getRecoveryCode().trim().toUpperCase(Locale.ROOT);
        if (user == null || recovery == null || recovery.getExpiresTime() == null
                || !recovery.getExpiresTime().isAfter(java.time.LocalDateTime.now())
                || !PasswordUtil.matches(submittedCode, recovery.getRecoveryHash())) {
            throw new IllegalArgumentException("用户名或恢复码错误");
        }
        if (!"ENABLED".equals(user.getStatus())) {
            throw new IllegalArgumentException("当前账号已被禁用");
        }
        if (PasswordUtil.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        if (userMapper.updatePassword(user.getId(), PasswordUtil.hash(request.getNewPassword())) != 1) {
            throw new IllegalStateException("密码重置失败");
        }
        String nextRecoveryCode = createAndStoreRecoveryCode(user.getId());
        tokenService.revokeAllForUser(user.getId());
        return new RecoveryCodeVO(nextRecoveryCode);
    }

    private String createAndStoreRecoveryCode(String userId) {
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        StringBuilder raw = new StringBuilder(19);
        for (int index = 0; index < bytes.length; index++) {
            if (index > 0 && index % 2 == 0) raw.append('-');
            raw.append(String.format(Locale.ROOT, "%02X", bytes[index] & 0xff));
        }
        SysPasswordRecovery recovery = new SysPasswordRecovery();
        recovery.setUserId(userId);
        recovery.setRecoveryHash(PasswordUtil.hash(raw.toString()));
        recovery.setExpiresTime(java.time.LocalDateTime.now().plusDays(recoveryCodeTtlDays));
        int changed = passwordRecoveryMapper.selectById(userId) == null
                ? passwordRecoveryMapper.insert(recovery)
                : passwordRecoveryMapper.updateById(recovery);
        if (changed != 1) throw new IllegalStateException("账号恢复码保存失败");
        return raw.toString();
    }

    private SysUser requireUser(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new NotFoundException("用户不存在");
        return user;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId()); vo.setUsername(user.getUsername()); vo.setStatus(user.getStatus());
        vo.setVipLevel(user.getVipLevel() == null ? 0 : user.getVipLevel());
        vo.setCreateTime(user.getCreateTime()); vo.setUpdateTime(user.getUpdateTime());
        Set<String> roles = userRoleMapper.findAllRoleCodesByUserId(user.getId());
        vo.setRoles(roles == null ? Set.of() : roles);
        return vo;
    }
}
