package com.ailearning.userservice.service.impl;

import com.ailearning.userservice.dto.request.KeycloakSyncRequest;
import com.ailearning.userservice.dto.request.LoginRequest;
import com.ailearning.userservice.dto.request.RegisterUserRequest;
import com.ailearning.userservice.dto.request.TokenRefreshRequest;
import com.ailearning.userservice.dto.request.UpdateUserRequest;
import com.ailearning.userservice.dto.request.UpdateUserStatusRequest;
import com.ailearning.userservice.dto.response.LoginResponse;
import com.ailearning.userservice.dto.response.RegisterUserResponse;
import com.ailearning.userservice.dto.response.UserProfileResponse;
import com.ailearning.userservice.entity.User;
import com.ailearning.userservice.enums.Role;
import com.ailearning.userservice.enums.UserStatus;
import com.ailearning.userservice.exception.EmailAlreadyExistsException;
import com.ailearning.userservice.exception.InvalidCredentialsException;
import com.ailearning.userservice.exception.RoleMismatchException;
import com.ailearning.userservice.exception.UserNameAlreadyExistsException;
import com.ailearning.userservice.exception.UserNotFoundException;
import com.ailearning.userservice.repository.UserRepository;
import com.ailearning.userservice.service.KeycloakAdminService;
import com.ailearning.userservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeycloakAdminService keycloakAdminService;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           KeycloakAdminService keycloakAdminService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.keycloakAdminService = keycloakAdminService;
    }

    @Override
    public RegisterUserResponse register(RegisterUserRequest request) {

        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new UserNameAlreadyExistsException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.STUDENT;
        UserStatus initialStatus = request.getAccountStatus() != null
                ? request.getAccountStatus()
                : (assignedRole == Role.CONTENT_CREATOR ? UserStatus.PENDING_VERIFICATION : UserStatus.ACTIVE);

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .contactNumber(request.getContactNumber())
                .accountStatus(initialStatus)
                .emailVerified(true)
                .skills(request.getSkills())
                .qualifications(request.getQualifications())
                .experienceYears(request.getExperienceYears())
                .preferredSubjects(request.getPreferredSubjects())
                .portfolioUrl(request.getPortfolioUrl())
                .bio(request.getBio())
                .build();

        user = userRepository.save(user);

        boolean kcSynced = false;
        String accessToken = null;
        String refreshToken = null;

        try {
            String kcId = keycloakAdminService.provisionOrUpdateUser(user, request.getPassword());
            if (kcId != null) {
                user.setKeycloakUserId(kcId);
                userRepository.save(user);
                kcSynced = true;
            }

            if (user.getAccountStatus() == UserStatus.ACTIVE) {
                Map<String, Object> tokenBundle = keycloakAdminService.exchangePasswordForToken(
                        user.getUsername(),
                        request.getPassword()
                );
                if (tokenBundle != null && tokenBundle.get("access_token") != null) {
                    accessToken = String.valueOf(tokenBundle.get("access_token"));
                    if (tokenBundle.get("refresh_token") != null) {
                        refreshToken = String.valueOf(tokenBundle.get("refresh_token"));
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Keycloak sync during registration warning: {}", ex.getMessage());
        }

        UserProfileResponse profile = mapToProfile(user);

        String responseMessage = (user.getAccountStatus() == UserStatus.PENDING_VERIFICATION)
                ? "Content Creator application submitted successfully. Your account is pending administrator approval."
                : "User registered successfully.";

        return RegisterUserResponse.builder()
                .message(responseMessage)
                .accountStatus(user.getAccountStatus())
                .keycloakSynced(kcSynced)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(profile)
                .build();
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        try {
            keycloakAdminService.deleteKeycloakUser(user.getKeycloakUserId(), user.getUsername(), user.getEmail());
        } catch (Exception ex) {
            log.warn("Keycloak deletion warning: {}", ex.getMessage());
        }

        userRepository.delete(user);
    }

    @Override
    public UserProfileResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setContactNumber(request.getContactNumber());

        user = userRepository.save(user);

        try {
            keycloakAdminService.provisionOrUpdateUser(user, null);
        } catch (Exception ex) {
            log.warn("Keycloak user update note: {}", ex.getMessage());
        }

        return mapToProfile(user);
    }

    @Override
    public UserProfileResponse updateUserStatus(Long id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setAccountStatus(request.getStatus());
        user = userRepository.save(user);

        try {
            boolean enable = request.getStatus() == UserStatus.ACTIVE;
            keycloakAdminService.setKeycloakUserEnabled(user.getKeycloakUserId(), user.getUsername(), user.getEmail(), enable);
        } catch (Exception ex) {
            log.warn("Keycloak user status update note: {}", ex.getMessage());
        }

        return mapToProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return mapToProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileResponse> getAllUsers(Role role) {
        List<User> list = (role != null) ? userRepository.findByRole(role) : userRepository.findAll();
        return list.stream().map(this::mapToProfile).collect(Collectors.toList());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getEmailOrUsername() != null ? request.getEmailOrUsername().trim() : "";
        if (identifier.isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidCredentialsException("Email/username and password are required.");
        }

        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email/username or password."));

        if (request.getExpectedRole() != null && user.getRole() != request.getExpectedRole()) {
            throw new RoleMismatchException("This account is registered as " + user.getRole()
                    + ". Please use the " + user.getRole() + " portal to sign in.");
        }

        boolean passwordMatches = false;
        try {
            passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } catch (Exception ignored) {
        }
        if (!passwordMatches && user.getPassword().equals(request.getPassword())) {
            passwordMatches = true;
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        }

        if (!passwordMatches) {
            throw new InvalidCredentialsException("Invalid email/username or password.");
        }

        if (user.getAccountStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new InvalidCredentialsException("Your Content Creator application is currently pending admin review. You will be able to log in once an administrator approves your account.");
        }

        if (user.getAccountStatus() == UserStatus.INACTIVE || user.getAccountStatus() == UserStatus.SUSPENDED) {
            throw new InvalidCredentialsException("Account is " + user.getAccountStatus() + ". Please contact administrator.");
        }

        try {
            if (user.getKeycloakUserId() == null) {
                String kcId = keycloakAdminService.provisionOrUpdateUser(user, request.getPassword());
                if (kcId != null) {
                    user.setKeycloakUserId(kcId);
                    userRepository.save(user);
                }
            }
        } catch (Exception ex) {
            log.debug("Keycloak user auto-provision note during login: {}", ex.getMessage());
        }

        String accessToken = null;
        String refreshToken = null;
        Long expiresIn = 3600L;
        String authProvider = "local-db";

        try {
            Map<String, Object> tokenBundle = keycloakAdminService.exchangePasswordForToken(
                    user.getUsername(),
                    request.getPassword()
            );
            if (tokenBundle == null || tokenBundle.get("access_token") == null) {
                tokenBundle = keycloakAdminService.exchangePasswordForToken(
                        user.getEmail(),
                        request.getPassword()
                );
            }
            if (tokenBundle != null && tokenBundle.get("access_token") != null) {
                accessToken = String.valueOf(tokenBundle.get("access_token"));
                if (tokenBundle.get("refresh_token") != null) {
                    refreshToken = String.valueOf(tokenBundle.get("refresh_token"));
                }
                if (tokenBundle.get("expires_in") instanceof Number num) {
                    expiresIn = num.longValue();
                }
                authProvider = "keycloak";
            }
        } catch (Exception ex) {
            log.warn("Keycloak token exchange error for user {}: {}", user.getUsername(), ex.getMessage());
        }

        return LoginResponse.builder()
                .message("Login successful")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .authProvider(authProvider)
                .user(mapToProfile(user))
                .build();
    }

    @Override
    public LoginResponse syncKeycloakSession(KeycloakSyncRequest request) {
        Map<String, Object> claims = keycloakAdminService.verifyAndExtractClaims(request.getAccessToken());
        if (claims == null || claims.isEmpty()) {
            throw new InvalidCredentialsException("Invalid Keycloak access token.");
        }

        String username = (String) claims.get("preferred_username");
        String email = (String) claims.get("email");
        String kcId = (String) claims.get("sub");
        String firstName = (String) claims.getOrDefault("given_name", "User");
        String lastName = (String) claims.getOrDefault("family_name", "");
        String rawRole = (String) claims.get("role");

        Role determinedRole = Role.STUDENT;
        if (rawRole != null) {
            try {
                determinedRole = Role.valueOf(rawRole.toUpperCase());
            } catch (Exception ignored) {
            }
        }

        if (request.getExpectedRole() != null && determinedRole != request.getExpectedRole()) {
            throw new RoleMismatchException("This account is registered as " + determinedRole
                    + ". Please use the " + determinedRole + " portal to sign in.");
        }

        Optional<User> existing = Optional.empty();
        if (email != null && !email.isBlank()) {
            existing = userRepository.findByEmailIgnoreCase(email);
        }
        if (existing.isEmpty() && username != null && !username.isBlank()) {
            existing = userRepository.findByUsernameIgnoreCase(username);
        }

        User user;
        if (existing.isPresent()) {
            user = existing.get();
            if (kcId != null && !kcId.equals(user.getKeycloakUserId())) {
                user.setKeycloakUserId(kcId);
            }
            if (user.getRole() == null) {
                user.setRole(determinedRole);
            }
            user = userRepository.save(user);
        } else {
            String fallbackUsername = (username != null && !username.isBlank())
                    ? username
                    : (email != null ? email.split("@")[0] : "user_" + System.currentTimeMillis());

            user = User.builder()
                    .username(fallbackUsername)
                    .email(email != null ? email.toLowerCase() : fallbackUsername + "@edugenai.local")
                    .password(passwordEncoder.encode("KeycloakOAuthSession!123"))
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(determinedRole)
                    .keycloakUserId(kcId)
                    .accountStatus(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .contactNumber("0770000000")
                    .build();
            user = userRepository.save(user);
        }

        return LoginResponse.builder()
                .message("Session synced successfully")
                .accessToken(request.getAccessToken())
                .refreshToken(request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(3600L)
                .authProvider("keycloak-sso")
                .user(mapToProfile(user))
                .build();
    }

    @Override
    public LoginResponse refreshToken(TokenRefreshRequest request) {
        Map<String, Object> tokens = keycloakAdminService.refreshAccessToken(request.getRefreshToken());
        if (tokens == null || tokens.get("access_token") == null) {
            throw new InvalidCredentialsException("Unable to refresh token. Session may have expired.");
        }

        String newAccess = String.valueOf(tokens.get("access_token"));
        String newRefresh = tokens.get("refresh_token") != null
                ? String.valueOf(tokens.get("refresh_token"))
                : request.getRefreshToken();
        Long exp = tokens.get("expires_in") instanceof Number n ? n.longValue() : 3600L;

        Map<String, Object> claims = keycloakAdminService.verifyAndExtractClaims(newAccess);
        UserProfileResponse userProfile = null;
        if (claims != null) {
            String username = (String) claims.get("preferred_username");
            String email = (String) claims.get("email");
            Optional<User> u = Optional.empty();
            if (email != null) u = userRepository.findByEmailIgnoreCase(email);
            if (u.isEmpty() && username != null) u = userRepository.findByUsernameIgnoreCase(username);
            userProfile = u.map(this::mapToProfile).orElse(null);
        }

        return LoginResponse.builder()
                .message("Token refreshed")
                .accessToken(newAccess)
                .refreshToken(newRefresh)
                .tokenType("Bearer")
                .expiresIn(exp)
                .authProvider("keycloak")
                .user(userProfile)
                .build();
    }

    private UserProfileResponse mapToProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .contactNumber(user.getContactNumber())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .skills(user.getSkills())
                .qualifications(user.getQualifications())
                .experienceYears(user.getExperienceYears())
                .preferredSubjects(user.getPreferredSubjects())
                .portfolioUrl(user.getPortfolioUrl())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
