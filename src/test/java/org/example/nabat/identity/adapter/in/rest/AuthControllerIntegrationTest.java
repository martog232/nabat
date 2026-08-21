package org.example.nabat.identity.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.in.security.JwtTokenProvider;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthControllerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepository;

    /** Prevent real SMTP connections during integration tests. */
    @MockitoBean
    @SuppressWarnings("unused")
    private EmailSender emailSender;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterNewUser() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "password123",
            "Test User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.refreshToken").value(notNullValue()))
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.displayName").value("Test User"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.notificationRadiusKm").value(5));
    }

    /**
     * The password policy is enforced at the edge, and the message names the rule that was
     * broken. A combined "must be 10+ chars with a letter and a digit" makes the user work
     * out which part they failed; this sends them straight to the fix.
     */
    @Test
    void shouldRejectRegistrationWithATooShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest("short@example.com", "passw0rd", "Short Pass");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").value("Password must be at least 10 characters"));
    }

    @Test
    void shouldRejectRegistrationWithAPasswordThatHasNoDigit() throws Exception {
        RegisterRequest request = new RegisterRequest("nodigit@example.com", "passwordonly", "No Digit");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").value("Password must contain at least one digit"));
    }

    /**
     * Reset carried its own {@code @Size(min = 6)}, so tightening registration alone would
     * have left "forgot password" as a supported way to set a password registration
     * refuses. The token is nonsense on purpose: validation runs before it is looked at, so
     * a 400 here proves the policy is checked at the edge rather than after the token.
     */
    @Test
    void shouldApplyTheSamePasswordPolicyToPasswordReset() throws Exception {
        var request = new ResetPasswordRequest("irrelevant-token", "passw0rd");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.newPassword").value("Password must be at least 10 characters"));
    }

    @Test
    void shouldPersistUserAfterRegistration() throws Exception {
        String email = "persisted-user@example.com";
        RegisterRequest request = new RegisterRequest(
            email,
            "password123",
            "Persisted User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        UserJpaEntity saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getDisplayName()).isEqualTo("Persisted User");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getNotificationRadiusKm()).isEqualTo(5);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldNotRegisterUserWithExistingEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "password123",
            "Test User"
        );

        // Register first time
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Try to register again with same email.
        //
        // 409, not 400: a duplicate is a conflict with existing state, not a malformed
        // request. This assertion said 400 because that is what the generic handler used to
        // return before EmailAlreadyRegisteredException got a mapping of its own; the
        // expectation was never updated, and nothing noticed because this whole class failed
        // at context load in CI and skips without Docker locally.
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        // First register a user
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "password123",
            "Test User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        // Then login
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.refreshToken").value(notNullValue()))
            .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    void shouldNotLoginWithInvalidPassword() throws Exception {
        // First register a user
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "password123",
            "Test User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        // Try to login with wrong password
        LoginRequest loginRequest = new LoginRequest("test@example.com", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRefreshToken() throws Exception {
        // Register and get tokens
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "password123",
            "Test User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        // Refresh token
        AuthController.RefreshTokenRequest refreshRequest = 
            new AuthController.RefreshTokenRequest(authResponse.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.refreshToken").value(notNullValue()));
    }

    @Test
    void shouldGetCurrentUserWithValidToken() throws Exception {
        // Register and get token
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "password123",
            "Test User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        // Get current user
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.notificationRadiusKm").value(5));
    }

    @Test
    void shouldIssueWebSocketTicketWithValidAccessToken() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "ws-ticket@example.com",
            "password123",
            "WS Ticket User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        mockMvc.perform(post("/api/v1/ws/tickets")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ticket").value(notNullValue()))
            .andExpect(jsonPath("$.expiresAt").value(notNullValue()));
    }

    @Test
    void shouldReturn401ForProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForWebSocketTicketEndpointWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/ws/tickets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenRefreshTokenIsUsedForProtectedEndpoint() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "refresh-as-access@example.com",
            "password123",
            "Refresh As Access User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + authResponse.refreshToken()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenExpiredRefreshTokenIsUsed() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "expired-refresh@example.com",
            "password123",
            "Expired Refresh User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        String expiredRefreshToken = Jwts.builder()
            .claims(Map.of(
                "userId", authResponse.user().id().toString(),
                "email", authResponse.user().email(),
                "role", authResponse.user().role().name(),
                JwtTokenProvider.TOKEN_TYPE, JwtTokenProvider.REFRESH_TOKEN_TYPE
            ))
            .subject(authResponse.user().email())
            .issuedAt(new Date(System.currentTimeMillis() - 60_000L))
            .expiration(new Date(System.currentTimeMillis() - 1_000L))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();

        AuthController.RefreshTokenRequest refreshRequest =
            new AuthController.RefreshTokenRequest(expiredRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenAccessTokenIsUsedAsRefreshToken() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "access-as-refresh@example.com",
            "password123",
            "Access As Refresh User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        AuthController.RefreshTokenRequest refreshRequest =
            new AuthController.RefreshTokenRequest(authResponse.accessToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenUserIsDisabledDuringRefresh() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "disabled-refresh@example.com",
            "password123",
            "Disabled Refresh User"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        UserJpaEntity persisted = userRepository.findByEmail(authResponse.user().email()).orElseThrow();
        persisted.setEnabled(false);
        userRepository.save(persisted);

        AuthController.RefreshTokenRequest refreshRequest =
            new AuthController.RefreshTokenRequest(authResponse.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized());
    }
}
