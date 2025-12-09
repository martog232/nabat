package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.out.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepository;

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
            .andExpect(jsonPath("$.user.role").value("USER"));
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

        // Try to register again with same email
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
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
            .andExpect(jsonPath("$.displayName").value("Test User"));
    }

    @Test
    void shouldReturn401ForProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }
}
