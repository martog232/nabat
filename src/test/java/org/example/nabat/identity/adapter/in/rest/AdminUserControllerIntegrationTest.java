package org.example.nabat.identity.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role split, exercised through the real filter chain and a real database.
 *
 * <p>Three things only this level can prove. That the {@code CHECK} constraint added in V12
 * actually accepts {@code MODERATOR} — a unit test with a mocked repository would pass against
 * a constraint that rejects every write. That the promotion takes effect for the promoted user
 * on their next request. And that the two gates compose: {@code @PreAuthorize} on the token's
 * claim, then the use case on the current row.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserRepository userRepository;

    /** Registration sends a verification email; there is no SMTP here. */
    @MockBean
    private EmailSender emailSender;

    private String adminToken;
    private UUID adminId;

    @BeforeEach
    void setUp() throws Exception {
        userJpaRepository.deleteAll();

        // Registered through the API so the password hash is whatever the application makes,
        // then promoted directly in the store — there is no bootstrap endpoint for the first
        // admin, and inventing one for a test would be testing something that does not exist.
        AuthResponse admin = register("role-admin@example.com", "Administrating1");
        adminId = admin.user().id();
        userRepository.save(
            userRepository.findById(UserId.of(adminId)).orElseThrow().withRole(Role.ADMIN)
        );
        // Re-login: the token from registration carries ROLE_USER, and @PreAuthorize reads
        // the token. A stale token is exactly what the second gate exists for, but this test
        // is about the happy path.
        adminToken = login("role-admin@example.com", "Administrating1").accessToken();
    }

    @Test
    void adminPromotesAUserToModeratorAndTheRoleSticks() throws Exception {
        AuthResponse target = register("role-target@example.com", "Moderating1234");

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", target.user().id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        // Read back through the repository, not the response: this is the assertion that the
        // V12 CHECK constraint permits the new value rather than rolling the write back.
        assertEquals(
            Role.MODERATOR,
            userRepository.findById(UserId.of(target.user().id())).orElseThrow().role()
        );
    }

    @Test
    void aFreshlyPromotedModeratorCanListAlertsAndAPlainUserCannot() throws Exception {
        AuthResponse target = register("role-lister@example.com", "Listing123456");

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + target.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", target.user().id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isOk());

        // A new token, because the old one still claims ROLE_USER. Roles are carried in the
        // token, so a promotion takes effect on the next sign-in — worth pinning, since the
        // alternative (a role change invalidating sessions) would be a deliberate choice.
        String moderatorToken = login("role-lister@example.com", "Listing123456").accessToken();

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void aModeratorCannotAdministerUsers() throws Exception {
        AuthResponse moderator = register("role-mod@example.com", "Moderator1234");
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", moderator.user().id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isOk());
        String moderatorToken = login("role-mod@example.com", "Moderator1234").accessToken();

        AuthResponse victim = register("role-victim@example.com", "Victimised123");

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", victim.user().id())
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCannotDemoteThemselvesOutOfTheOnlyRoleThatCouldUndoIt() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());

        assertEquals(Role.ADMIN, userRepository.findById(UserId.of(adminId)).orElseThrow().role());
    }

    @Test
    void disablingAnAccountEndsItsSessionImmediately() throws Exception {
        AuthResponse target = register("role-disabled@example.com", "Disabling1234");
        String targetToken = target.accessToken();

        // Works before.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/enabled", target.user().id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // And not after — the same token, rejected because disable() bumped the token version.
        // Not "at expiry": a disabled account that keeps working for an hour is not disabled.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        assertFalse(userRepository.findById(UserId.of(target.user().id())).orElseThrow().enabled());
    }

    private AuthResponse register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            new RegisterRequest(email, password, "Role Test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private AuthResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }
}
