package com.ailearning.userservice.service;

import com.ailearning.userservice.entity.User;
import com.ailearning.userservice.enums.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.server-url:http://localhost:8090}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm:ai-learning-platform}")
    private String realm;

    @Value("${keycloak.client-id:edugenai-frontend}")
    private String clientId;

    @Value("${keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin-password:admin}")
    private String adminPassword;

    private volatile boolean realmVerified = false;

    public KeycloakAdminService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Obtain a master-realm admin access token from Keycloak.
     */
    public String getMasterAdminToken() {
        String tokenUrl = keycloakServerUrl + "/realms/master/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        ResponseEntity<Map> resp = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(form, headers), Map.class);
        if (resp.getBody() != null && resp.getBody().get("access_token") != null) {
            return String.valueOf(resp.getBody().get("access_token"));
        }
        throw new IllegalStateException("Failed to obtain Keycloak master admin token");
    }

    /**
     * Self-healing bootstrap: ensures the `ai-learning-platform` realm, roles (`STUDENT`,
     * `CONTENT_CREATOR`, `ADMIN`), and OIDC clients (`edugenai-frontend`, `api-gateway-service`)
     * exist in Keycloak even when starting with an existing or empty Keycloak volume.
     */
    public synchronized void ensureRealmConfigured() {
        if (realmVerified) {
            return;
        }
        String adminToken = getMasterAdminToken();
        HttpHeaders headers = bearerJsonHeaders(adminToken);

        // 1. Check / create realm
        String realmUrl = keycloakServerUrl + "/admin/realms/" + realm;
        try {
            restTemplate.exchange(realmUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        } catch (HttpClientErrorException.NotFound nf) {
            log.info("Creating Keycloak realm '{}'...", realm);
            Map<String, Object> realmPayload = new LinkedHashMap<>();
            realmPayload.put("realm", realm);
            realmPayload.put("enabled", true);
            realmPayload.put("displayName", "EduGenAI Learning Platform");
            realmPayload.put("registrationAllowed", true);
            realmPayload.put("loginWithEmailAllowed", true);
            realmPayload.put("resetPasswordAllowed", true);
            realmPayload.put("verifyEmail", false);
            restTemplate.exchange(
                    keycloakServerUrl + "/admin/realms",
                    HttpMethod.POST,
                    new HttpEntity<>(realmPayload, headers),
                    Void.class
            );
        }

        // 2. Ensure realm roles exist
        for (Role r : Role.values()) {
            ensureRealmRoleExists(adminToken, r.name());
        }

        // 3. Ensure public frontend OIDC client exists
        ensureFrontendClientExists(adminToken);

        // 4. Ensure confidential gateway OIDC client exists
        ensureGatewayClientExists(adminToken);

        realmVerified = true;
        log.info("Keycloak realm '{}', roles, and OIDC clients verified.", realm);
    }

    private void ensureRealmRoleExists(String adminToken, String roleName) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        String roleUrl = keycloakServerUrl + "/admin/realms/" + realm + "/roles/" + roleName;
        try {
            restTemplate.exchange(roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        } catch (HttpClientErrorException.NotFound nf) {
            Map<String, Object> roleBody = new LinkedHashMap<>();
            roleBody.put("name", roleName);
            roleBody.put("description", "EduGenAI role " + roleName);
            restTemplate.exchange(
                    keycloakServerUrl + "/admin/realms/" + realm + "/roles",
                    HttpMethod.POST,
                    new HttpEntity<>(roleBody, headers),
                    Void.class
            );
        }
    }

    private void ensureFrontendClientExists(String adminToken) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        URI searchUri = UriComponentsBuilder
                .fromUriString(keycloakServerUrl + "/admin/realms/" + realm + "/clients")
                .queryParam("clientId", clientId)
                .build()
                .encode()
                .toUri();
        ResponseEntity<List> existing = restTemplate.exchange(searchUri, HttpMethod.GET, new HttpEntity<>(headers), List.class);
        if (existing.getBody() != null && !existing.getBody().isEmpty()) {
            return;
        }
        Map<String, Object> clientBody = new LinkedHashMap<>();
        clientBody.put("clientId", clientId);
        clientBody.put("name", "EduGenAI Frontend Portals");
        clientBody.put("enabled", true);
        clientBody.put("publicClient", true);
        clientBody.put("protocol", "openid-connect");
        clientBody.put("standardFlowEnabled", true);
        clientBody.put("directAccessGrantsEnabled", true);
        clientBody.put("fullScopeAllowed", true);
        clientBody.put("redirectUris", List.of(
                "http://localhost:3001/*",
                "http://localhost:3002/*",
                "http://localhost:3003/*",
                "http://localhost:5173/*",
                "http://localhost:5174/*",
                "http://localhost:5175/*",
                "http://127.0.0.1:3001/*",
                "http://127.0.0.1:3002/*",
                "http://127.0.0.1:3003/*",
                "http://127.0.0.1:5173/*",
                "http://127.0.0.1:5174/*",
                "http://127.0.0.1:5175/*"
        ));
        clientBody.put("webOrigins", List.of("*"));
        restTemplate.exchange(
                keycloakServerUrl + "/admin/realms/" + realm + "/clients",
                HttpMethod.POST,
                new HttpEntity<>(clientBody, headers),
                Void.class
        );
    }

    private void ensureGatewayClientExists(String adminToken) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        URI searchUri = UriComponentsBuilder
                .fromUriString(keycloakServerUrl + "/admin/realms/" + realm + "/clients")
                .queryParam("clientId", "api-gateway-service")
                .build()
                .encode()
                .toUri();
        ResponseEntity<List> existing = restTemplate.exchange(searchUri, HttpMethod.GET, new HttpEntity<>(headers), List.class);
        if (existing.getBody() != null && !existing.getBody().isEmpty()) {
            return;
        }
        Map<String, Object> clientBody = new LinkedHashMap<>();
        clientBody.put("clientId", "api-gateway-service");
        clientBody.put("name", "EduGenAI API Gateway");
        clientBody.put("enabled", true);
        clientBody.put("publicClient", false);
        clientBody.put("secret", "gateway-secret");
        clientBody.put("protocol", "openid-connect");
        clientBody.put("standardFlowEnabled", true);
        clientBody.put("directAccessGrantsEnabled", true);
        clientBody.put("serviceAccountsEnabled", true);
        clientBody.put("fullScopeAllowed", true);
        clientBody.put("redirectUris", List.of("http://localhost:*"));
        clientBody.put("webOrigins", List.of("*"));
        restTemplate.exchange(
                keycloakServerUrl + "/admin/realms/" + realm + "/clients",
                HttpMethod.POST,
                new HttpEntity<>(clientBody, headers),
                Void.class
        );
    }

    /**
     * Provision or update a user inside Keycloak, set their password, and assign their Realm Role.
     * Returns the Keycloak user UUID, or null if Keycloak is unreachable.
     */
    public String provisionOrUpdateUser(User user, String rawPassword) {
        try {
            ensureRealmConfigured();
            String adminToken = getMasterAdminToken();
            HttpHeaders headers = bearerJsonHeaders(adminToken);

            String kcUserId = user.getKeycloakUserId();
            if (kcUserId == null || kcUserId.isBlank()) {
                kcUserId = findKeycloakUserId(adminToken, user.getUsername(), user.getEmail());
            }

            Map<String, Object> userRep = new LinkedHashMap<>();
            userRep.put("username", user.getUsername());
            userRep.put("email", user.getEmail());
            userRep.put("firstName", user.getFirstName());
            userRep.put("lastName", user.getLastName());
            userRep.put("enabled", user.getAccountStatus() == com.ailearning.userservice.enums.UserStatus.ACTIVE);
            userRep.put("emailVerified", true);
            userRep.put("requiredActions", Collections.emptyList());

            if (kcUserId == null) {
                ResponseEntity<Void> createResp = restTemplate.exchange(
                        keycloakServerUrl + "/admin/realms/" + realm + "/users",
                        HttpMethod.POST,
                        new HttpEntity<>(userRep, headers),
                        Void.class
                );
                URI location = createResp.getHeaders().getLocation();
                if (location != null) {
                    String path = location.getPath();
                    kcUserId = path.substring(path.lastIndexOf('/') + 1);
                } else {
                    kcUserId = findKeycloakUserId(adminToken, user.getUsername(), user.getEmail());
                }
            } else {
                restTemplate.exchange(
                        keycloakServerUrl + "/admin/realms/" + realm + "/users/" + kcUserId,
                        HttpMethod.PUT,
                        new HttpEntity<>(userRep, headers),
                        Void.class
                );
            }

            if (kcUserId != null && rawPassword != null && !rawPassword.isBlank()) {
                Map<String, Object> cred = new LinkedHashMap<>();
                cred.put("type", "password");
                cred.put("value", rawPassword);
                cred.put("temporary", false);
                restTemplate.exchange(
                        keycloakServerUrl + "/admin/realms/" + realm + "/users/" + kcUserId + "/reset-password",
                        HttpMethod.PUT,
                        new HttpEntity<>(cred, headers),
                        Void.class
                );
            }

            if (kcUserId != null && user.getRole() != null) {
                assignRealmRole(adminToken, kcUserId, user.getRole().name());
            }

            return kcUserId;
        } catch (Exception ex) {
            log.warn("Keycloak user sync warning for '{}': {}", user.getUsername(), ex.getMessage());
            return null;
        }
    }

    private String findKeycloakUserId(String adminToken, String username, String email) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        if (username != null && !username.isBlank()) {
            URI byUsername = UriComponentsBuilder
                    .fromUriString(keycloakServerUrl + "/admin/realms/" + realm + "/users")
                    .queryParam("username", username)
                    .queryParam("exact", true)
                    .build()
                    .encode()
                    .toUri();
            ResponseEntity<List> resp = restTemplate.exchange(byUsername, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                Object first = resp.getBody().get(0);
                if (first instanceof Map<?, ?> m && m.get("id") != null) {
                    return String.valueOf(m.get("id"));
                }
            }
        }
        if (email != null && !email.isBlank()) {
            URI byEmail = UriComponentsBuilder
                    .fromUriString(keycloakServerUrl + "/admin/realms/" + realm + "/users")
                    .queryParam("email", email)
                    .queryParam("exact", true)
                    .build()
                    .encode()
                    .toUri();
            ResponseEntity<List> resp = restTemplate.exchange(byEmail, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                Object first = resp.getBody().get(0);
                if (first instanceof Map<?, ?> m && m.get("id") != null) {
                    return String.valueOf(m.get("id"));
                }
            }
        }
        return null;
    }

    private void assignRealmRole(String adminToken, String kcUserId, String roleName) {
        ensureRealmRoleExists(adminToken, roleName);
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<Map> roleResp = restTemplate.exchange(
                keycloakServerUrl + "/admin/realms/" + realm + "/roles/" + roleName,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        if (roleResp.getBody() != null) {
            restTemplate.exchange(
                    keycloakServerUrl + "/admin/realms/" + realm + "/users/" + kcUserId + "/role-mappings/realm",
                    HttpMethod.POST,
                    new HttpEntity<>(List.of(roleResp.getBody()), headers),
                    Void.class
            );
        }
    }

    /**
     * Exchange username/email and password for a signed Keycloak OIDC token bundle.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangePasswordForToken(String usernameOrEmail, String password) {
        try {
            ensureRealmConfigured();
            String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "password");
            form.add("client_id", clientId);
            form.add("username", usernameOrEmail);
            form.add("password", password);
            form.add("scope", "openid profile email");

            ResponseEntity<Map> resp = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(form, headers), Map.class);
            return resp.getBody();
        } catch (Exception ex) {
            log.debug("Keycloak direct token exchange note for '{}': {}", usernameOrEmail, ex.getMessage());
            return null;
        }
    }

    /**
     * Refresh a Keycloak access token using a refresh_token.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshAccessToken(String refreshToken) {
        String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);

        ResponseEntity<Map> resp = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(form, headers), Map.class);
        return resp.getBody();
    }

    /**
     * Verify a Keycloak access token via Keycloak's /userinfo endpoint (falling back to JWT claim inspection)
     * and return normalized user claims.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verifyAndExtractClaims(String accessToken) {
        Map<String, Object> jwtClaims = decodeJwtPayload(accessToken);
        try {
            String userInfoUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            ResponseEntity<Map> resp = restTemplate.exchange(userInfoUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getBody() != null) {
                Map<String, Object> merged = new LinkedHashMap<>(jwtClaims);
                merged.putAll(resp.getBody());
                return merged;
            }
        } catch (HttpClientErrorException.Unauthorized unauth) {
            throw unauth;
        } catch (Exception ex) {
            log.debug("Keycloak userinfo fallback to JWT payload: {}", ex.getMessage());
        }
        return jwtClaims;
    }

    public Map<String, Object> decodeJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return Collections.emptyMap();
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public void setKeycloakUserEnabled(String kcUserId, String username, String email, boolean enabled) {
        try {
            String adminToken = getMasterAdminToken();
            String resolvedId = (kcUserId != null && !kcUserId.isBlank())
                    ? kcUserId
                    : findKeycloakUserId(adminToken, username, email);
            if (resolvedId == null) {
                return;
            }
            HttpHeaders headers = bearerJsonHeaders(adminToken);
            Map<String, Object> patch = Map.of("enabled", enabled);
            restTemplate.exchange(
                    keycloakServerUrl + "/admin/realms/" + realm + "/users/" + resolvedId,
                    HttpMethod.PUT,
                    new HttpEntity<>(patch, headers),
                    Void.class
            );
        } catch (Exception ex) {
            log.warn("Unable to update Keycloak enabled state for '{}': {}", username, ex.getMessage());
        }
    }

    public void deleteKeycloakUser(String kcUserId, String username, String email) {
        try {
            String adminToken = getMasterAdminToken();
            String resolvedId = (kcUserId != null && !kcUserId.isBlank())
                    ? kcUserId
                    : findKeycloakUserId(adminToken, username, email);
            if (resolvedId == null) {
                return;
            }
            HttpHeaders headers = bearerJsonHeaders(adminToken);
            restTemplate.exchange(
                    keycloakServerUrl + "/admin/realms/" + realm + "/users/" + resolvedId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );
        } catch (Exception ex) {
            log.warn("Unable to delete Keycloak user '{}': {}", username, ex.getMessage());
        }
    }

    private HttpHeaders bearerJsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

