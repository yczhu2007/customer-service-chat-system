package com.example.customerservice.service;

import com.example.customerservice.dto.ProfilePasswordUpdateDTO;
import com.example.customerservice.dto.ProfileUpdateDTO;
import com.example.customerservice.dto.UserRegisterDTO;
import com.example.customerservice.dto.UserVO;
import com.example.customerservice.dto.PasswordResetDTO;
import com.example.customerservice.dto.RecoveryCodeVO;
import com.example.customerservice.dto.UserRegistrationVO;

public interface UserAccountService {
    UserRegistrationVO register(UserRegisterDTO request);
    UserVO getProfile(String userId);
    UserVO updateProfile(String userId, ProfileUpdateDTO request);
    void updatePassword(String userId, ProfilePasswordUpdateDTO request);
    RecoveryCodeVO regenerateRecoveryCode(String userId);
    RecoveryCodeVO resetPassword(PasswordResetDTO request);
}
