package com.ailearning.userservice.config;

import com.ailearning.userservice.entity.User;
import com.ailearning.userservice.enums.Role;
import com.ailearning.userservice.enums.UserStatus;
import com.ailearning.userservice.repository.UserRepository;
import com.ailearning.userservice.service.KeycloakAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DefaultUsersInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultUsersInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeycloakAdminService keycloakAdminService;

    public DefaultUsersInitializer(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   KeycloakAdminService keycloakAdminService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.keycloakAdminService = keycloakAdminService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultUsers() {
        try {
            ensureUser("admin", "admin@edugenai.com", "Admin@123", "System", "Administrator", Role.ADMIN, "0770000001");
            ensureUser("creator", "creator@edugenai.com", "Creator@123", "Alex", "Rivers", Role.CONTENT_CREATOR, "0770000002");
            ensureUser("student", "student@edugenai.com", "Student@123", "Alex", "Explorer", Role.STUDENT, "0770000003");
            log.info("Default seed users initialized successfully.");
        } catch (Exception ex) {
            log.warn("Default users initializer encountered non-fatal note: {}", ex.getMessage());
        }
    }

    private void ensureUser(String username, String email, String rawPassword,
                            String firstName, String lastName, Role role, String contactNumber) {
        Optional<User> existing = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByEmailIgnoreCase(email));

        User user;
        if (existing.isPresent()) {
            user = existing.get();
        } else {
            user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(role)
                    .contactNumber(contactNumber)
                    .accountStatus(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build();
            user = userRepository.save(user);
            log.info("Created seed user: {} ({})", username, role);
        }

        try {
            String kcId = keycloakAdminService.provisionOrUpdateUser(user, rawPassword);
            if (kcId != null && (user.getKeycloakUserId() == null || !kcId.equals(user.getKeycloakUserId()))) {
                user.setKeycloakUserId(kcId);
                userRepository.save(user);
            }
        } catch (Exception ex) {
            log.debug("Keycloak provisioning note for {}: {}", username, ex.getMessage());
        }
    }
}

