package org.example.nabat.identity.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two flows that depend on a verification token surviving a write.
 *
 * <p>Both were dead: the token's id is a 43-character hash and the column held 36, so every
 * insert was rejected and there was never anything to redeem. Ten unit tests passed
 * throughout, because they mocked the repository, and the existing integration tests
 * registered users without ever verifying one — the gap was not a missing assertion but a
 * missing path.
 *
 * <p>The secret is taken from the mocked {@link EmailSender}, which is where a real user gets
 * it. Reading it out of the database instead would test nothing: what is stored is the hash,
 * and the whole point is that the emailed value maps onto it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationFlowIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userRepository;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void aUserCanVerifyTheEmailAddressTheyRegisteredWith() throws Exception {
        register("verify-me@example.com", "Verifying1234");
        assertThat(emailVerifiedFor("verify-me@example.com")).isFalse();

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(
            org.mockito.ArgumentMatchers.eq("verify-me@example.com"),
            org.mockito.ArgumentMatchers.anyString(),
            token.capture()
        );

        mockMvc.perform(post("/api/v1/auth/verify").param("token", token.getValue()))
                .andExpect(status().isOk());

        assertThat(emailVerifiedFor("verify-me@example.com"))
            .as("the flag the whole feature exists to set")
            .isTrue();
    }

    /** Single use: the token is marked used, so a replay of the same link is refused. */
    @Test
    void aVerificationTokenCannotBeUsedTwice() throws Exception {
        register("verify-twice@example.com", "Verifying1234");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(
            org.mockito.ArgumentMatchers.eq("verify-twice@example.com"),
            org.mockito.ArgumentMatchers.anyString(),
            token.capture()
        );

        mockMvc.perform(post("/api/v1/auth/verify").param("token", token.getValue()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/verify").param("token", token.getValue()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void anUnknownTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify").param("token", "not-a-real-token"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aForgottenPasswordCanActuallyBeReset() throws Exception {
        String email = "reset-me@example.com";
        register(email, "Original12345");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordResetEmail(
            org.mockito.ArgumentMatchers.eq(email),
            org.mockito.ArgumentMatchers.anyString(),
            token.capture()
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\",\"newPassword\":\"Replaced12345\"}"))
                .andExpect(status().isOk());

        // The old password stops working and the new one starts: a reset that returns 200 and
        // changes nothing is the failure this flow had.
        login(email, "Original12345").andExpect(status().isUnauthorized());
        login(email, "Replaced12345").andExpect(status().isOk());
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                 + "\",\"displayName\":\"Flow Test\"}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private boolean emailVerifiedFor(String email) {
        return userRepository.findByEmail(email).orElseThrow().isEmailVerified();
    }
}
