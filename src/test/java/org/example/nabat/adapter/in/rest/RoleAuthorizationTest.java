package org.example.nabat.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-15: verifies @PreAuthorize on {@code GET /api/v1/alerts} (admin-only).
 * Uses @SpringBootTest so the real SecurityConfig (with @EnableMethodSecurity) is loaded.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listAlerts_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
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
}
