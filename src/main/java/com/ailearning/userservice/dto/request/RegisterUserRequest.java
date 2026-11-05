
package com.ailearning.userservice.dto.request;

import com.ailearning.userservice.enums.Role;
import com.ailearning.userservice.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequest {

    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    private String lastName;

    @NotBlank
    @Size(max = 50)
    private String username;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    private String contactNumber;

    private Role role;

    private UserStatus accountStatus;

    private String skills;

    private String qualifications;

    private Integer experienceYears;

    private String preferredSubjects;

    private String portfolioUrl;

    private String bio;
}
