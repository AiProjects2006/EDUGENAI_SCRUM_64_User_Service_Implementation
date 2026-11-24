package com.ailearning.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Email
    private String email;

    private String contactNumber;

    private String avatarUrl;

    private String bio;

    private String qualifications;

    private String skills;
}
