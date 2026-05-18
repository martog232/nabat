package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.RateLimitingFilter;
import org.example.nabat.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for T-44 — verifies that the rate limiting filter returns
 * 429 after the configured capacity is exhausted on auth endpoints.
 *
 * Uses a very small window (capacity=2) to keep the test fast.
 */
@SpringBootTest(properties = {
        "nabat.rate-limit.auth.capacity=2",
        "nabat.rate-limit.auth.window-minutes=15"
})
@AutoConfigureMockMvc
class RateLimitingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    /** Suppress SMTP connections triggered by registration. */
    @MockBean
    private EmailSender emailSender;

    @BeforeEach
    void resetBucket() {
        rateLimitingFilter.resetBuckets();
    }

    @Test
    void loginReturns429AfterCapacityExhausted() throws Exception {
        LoginRequest bad = new LoginRequest("nobody@example.com", "wrongpass");
        String body = objectMapper.writeValueAsString(bad);

        // First 2 requests: rejected for bad credentials but NOT rate-limited
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        // 3rd request: rate-limited → 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }

    @Test
    void registerReturns429AfterCapacityExhausted() throws Exception {
        RegisterRequest req = new RegisterRequest("a@b.com", "pass1234567", "A");
        String body = objectMapper.writeValueAsString(req);

        // First 2 requests go through (first succeeds, second is 400 duplicate email — both pass the filter)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)); // status doesn't matter here
        }

        // 3rd request must be rate-limited
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void nonAuthEndpointsAreNotRateLimited() throws Exception {
        // This endpoint is JWT-protected, so all requests get 401.
        // The important thing is they are NOT 429.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/alerts/nearby"))
                    .andExpect(status().isUnauthorized());
        }
    }
}

