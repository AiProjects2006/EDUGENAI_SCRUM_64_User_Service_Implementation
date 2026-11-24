package com.ailearning.userservice.dto.request;

import com.ailearning.userservice.enums.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KeycloakSyncRequest {

    @NotBlank
    private String accessToken;

    private String refreshToken;

    private Role expectedRole;
}

