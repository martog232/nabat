package org.example.nabat.platform;

import org.example.nabat.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses @SpringBootTest so the real SecurityConfig (with @EnableMethodSecurity) is loaded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RoleAuthorizationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listAlerts_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    /** Triaging reports is the moderator's job; it should not require account admin rights. */
    @Test
    void listAlerts_asModerator_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").with(user("m").roles("MODERATOR")))
                .andExpect(status().isOk());
    }

    @Test
    void listAlerts_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void listAlerts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The other half of the split: a moderator must not reach account administration. This is
     * the assertion that would fail if someone "simplified" the two capabilities back into one
     * role.
     */
    @Test
    void changeRole_asModerator_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", UUID.randomUUID())
                        .with(user("m").roles("MODERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_asUser_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", UUID.randomUUID())
                        .with(user("u").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setEnabled_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/enabled", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }
}
