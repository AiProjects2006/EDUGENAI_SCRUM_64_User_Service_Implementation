package com.ailearning.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponse {

    private String message;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String authProvider;
    private UserProfileResponse user;

    public LoginResponse(String message) {
        this.message = message;
    }
}
