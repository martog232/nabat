package org.example.nabat.identity.config;

import org.example.nabat.identity.adapter.in.rest.AuthController;
import org.example.nabat.identity.adapter.in.security.JwtAuthenticationFilter;
import org.example.nabat.identity.application.port.in.ForgotPasswordUseCase;
import org.example.nabat.identity.application.port.in.LoginUserUseCase;
import org.example.nabat.identity.application.port.in.RefreshTokenUseCase;
import org.example.nabat.identity.application.port.in.RegisterUserUseCase;
import org.example.nabat.identity.application.port.in.ResetPasswordUseCase;
import org.example.nabat.identity.application.port.in.VerifyEmailUseCase;
import org.example.nabat.identity.application.port.out.TokenProvider;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.shared.config.AllowedOrigins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which {@code /api/v1/auth} endpoints are public, exercised through the real filter chain.
 *
 * <h2>Why this exists</h2>
 * The rule used to be {@code requestMatchers("/api/v1/auth/**").permitAll()}, which also
 * matched {@code GET /api/v1/auth/me} — the endpoint returning the caller's own profile.
 * {@code AuthController} documents the opposite and therefore omits a null check, so an
 * anonymous request reached {@code UserResponse.from(null)} and died on
 * {@code user.id()}: a 500 where a 401 was intended.
 *
 * <p>{@code AuthControllerIntegrationTest} already asserted the 401. It never caught this,
 * because it needs Testcontainers and is skipped wherever Docker is absent — which is
 * exactly where a developer would notice. This test needs no database, so the rule is
 * checked on every run.
 *
 * <p>Filters are deliberately left enabled, unlike the controller slice tests that use
 * {@code @AutoConfigureMockMvc(addFilters = false)}: the filter chain is the subject here,
 * not the controller.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AllowedOrigins.class})
class AuthEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // Collaborators of the real JwtAuthenticationFilter. No stubbing needed: every request
    // below is either anonymous or carries a token the mock provider rejects by default.
    @MockitoBean
    private TokenProvider tokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;
    @MockitoBean
    private LoginUserUseCase loginUserUseCase;
    @MockitoBean
    private RefreshTokenUseCase refreshTokenUseCase;
    @MockitoBean
    private VerifyEmailUseCase verifyEmailUseCase;
    @MockitoBean
    private ForgotPasswordUseCase forgotPasswordUseCase;
    @MockitoBean
    private ResetPasswordUseCase resetPasswordUseCase;

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsAnUnparseableToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer nonsense"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsPublic() throws Exception {
        // 400 rather than 401: the request reaches the controller and fails bean
        // validation on an empty body, which is what "public" looks like here.
        mockMvc.perform(post("/api/v1/auth/login"))
            .andExpect(status().is4xxClientError())
            .andExpect(status().is(org.springframework.http.HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void registerIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register"))
            .andExpect(status().is(org.springframework.http.HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void aGetOnAPublicPostEndpointIsNotItselfPublic() throws Exception {
        // The permit list is method-scoped, so GET /login does not inherit POST /login's
        // exemption. It falls through to the /api/v1/** authenticated rule.
        mockMvc.perform(get("/api/v1/auth/login"))
            .andExpect(status().isUnauthorized());
    }
}
