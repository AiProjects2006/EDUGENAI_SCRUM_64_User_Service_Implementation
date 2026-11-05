package com.ailearning.userservice.dto.response;

import com.ailearning.userservice.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterUserResponse {

    private String message;
    private UserStatus accountStatus;
    private Boolean keycloakSynced;
    private String accessToken;
    private String refreshToken;
    private UserProfileResponse user;

    public RegisterUserResponse(String message, UserStatus accountStatus) {
        this.message = message;
        this.accountStatus = accountStatus;
    }
}
