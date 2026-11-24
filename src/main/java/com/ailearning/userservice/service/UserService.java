package com.ailearning.userservice.service;

import com.ailearning.userservice.dto.request.KeycloakSyncRequest;
import com.ailearning.userservice.dto.request.LoginRequest;
import com.ailearning.userservice.dto.request.RegisterUserRequest;
import com.ailearning.userservice.dto.request.TokenRefreshRequest;
import com.ailearning.userservice.dto.request.UpdateUserRequest;
import com.ailearning.userservice.dto.request.UpdateUserStatusRequest;
import com.ailearning.userservice.dto.response.LoginResponse;
import com.ailearning.userservice.dto.response.RegisterUserResponse;
import com.ailearning.userservice.dto.response.UserProfileResponse;
import com.ailearning.userservice.enums.Role;

import java.util.List;

public interface UserService {

    RegisterUserResponse register(RegisterUserRequest request);

    void deleteUser(Long id);

    UserProfileResponse updateUser(Long id, UpdateUserRequest request);

    UserProfileResponse updateUserStatus(Long id, UpdateUserStatusRequest request);

    UserProfileResponse getUserProfile(Long id);

    List<UserProfileResponse> getAllUsers(Role role);

    LoginResponse login(LoginRequest request);

    LoginResponse syncKeycloakSession(KeycloakSyncRequest request);

    LoginResponse refreshToken(TokenRefreshRequest request);

}
