package com.ailearning.userservice.dto.response;

import com.ailearning.userservice.enums.Role;
import com.ailearning.userservice.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String contactNumber;
    private Role role;
    private UserStatus accountStatus;

    private String skills;
    private String qualifications;
    private Integer experienceYears;
    private String preferredSubjects;
    private String portfolioUrl;
    private String bio;
    private LocalDateTime createdAt;
}

