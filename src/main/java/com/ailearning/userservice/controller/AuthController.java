package com.ailearning.userservice.controller;

import com.ailearning.userservice.dto.request.KeycloakSyncRequest;
import com.ailearning.userservice.dto.request.LoginRequest;
import com.ailearning.userservice.dto.request.RegisterUserRequest;
import com.ailearning.userservice.dto.request.TokenRefreshRequest;
import com.ailearning.userservice.dto.response.LoginResponse;
import com.ailearning.userservice.dto.response.RegisterUserResponse;
import com.ailearning.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Login endpoint (alias to /api/users/login)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @Operation(summary = "Register endpoint (alias to /api/users/register)")
    @PostMapping("/register")
    public RegisterUserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return userService.register(request);
    }

    @Operation(summary = "Refresh token endpoint")
    @PostMapping("/refresh")
    public LoginResponse refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return userService.refreshToken(request);
    }

    @Operation(summary = "Keycloak OIDC session sync endpoint")
    @PostMapping("/keycloak-sync")
    public LoginResponse syncKeycloakSession(@Valid @RequestBody KeycloakSyncRequest request) {
        return userService.syncKeycloakSession(request);
    }
}
