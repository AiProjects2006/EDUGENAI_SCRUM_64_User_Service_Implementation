package com.ailearning.userservice.dto.request;

import com.ailearning.userservice.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotNull
    private UserStatus status;
}

