package com.ailearning.userservice.controller;

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
import com.ailearning.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List all users (optional filter by role)")
    @GetMapping
    public List<UserProfileResponse> getAllUsers(@RequestParam(required = false) Role role) {
        return userService.getAllUsers(role);
    }

    @Operation(summary = "Register a new user in User DB and Keycloak")
    @PostMapping("/register")
    public RegisterUserResponse register(
            @Valid @RequestBody RegisterUserRequest request) {

        return userService.register(request);
    }

    @Operation(summary = "Delete user by ID")
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    @Operation(summary = "Update user profile")
    @PutMapping("/{id}")
    public UserProfileResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        return userService.updateUser(id, request);
    }

    @Operation(summary = "Update user account status (ACTIVE, SUSPENDED, INACTIVE)")
    @PatchMapping("/{id}/status")
    public UserProfileResponse updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {

        return userService.updateUserStatus(id, request);
    }

    @Operation(summary = "Get user profile")
    @GetMapping("/{id}")
    public UserProfileResponse getUserProfile(@PathVariable Long id) {

        return userService.getUserProfile(id);
    }

    @Operation(summary = "Authenticate user via User DB + Keycloak OIDC")
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        return userService.login(request);
    }

    @Operation(summary = "Synchronize a Keycloak OIDC SSO token with User DB")
    @PostMapping("/keycloak-sync")
    public LoginResponse syncKeycloakSession(
            @Valid @RequestBody KeycloakSyncRequest request) {

        return userService.syncKeycloakSession(request);
    }

    @Operation(summary = "Refresh Keycloak OIDC access token")
    @PostMapping("/refresh")
    public LoginResponse refreshToken(
            @Valid @RequestBody TokenRefreshRequest request) {

        return userService.refreshToken(request);
    }

}
