package org.example.nabat.identity.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.in.ProvisionOfficialsUseCase;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An authority's own account invites a colleague, who sets a password from the invitation and
 * signs in as an official of their own — and only the authority's account manages its staff.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorityMembersIntegrationTest extends PostgresTestSupport {

    private static final String PASSWORD = "Rayon2026pass";

    @Autowired private ProvisionOfficialsUseCase provisioning;
    @Autowired private UserJpaRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmailSender emailSender;

    private String lead;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM verification_tokens");
        users.deleteAll();
        provisioning.provision(PASSWORD);
        lead = login("lozenets@rayon.nabat.test", PASSWORD);
    }

    @Test
    void aColleagueIsInvitedSetsAPasswordAndActsUnderTheirOwnName() throws Exception {
        perform(invite("ivan@example.com", "Иван Петров"), lead)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("OFFICIAL"))
            .andExpect(jsonPath("$.authorityCode").value("LOZENETS"));

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendInvitationEmail(eq("ivan@example.com"), eq("Иван Петров"), eq("Район Лозенец"),
            eq("Район Лозенец"), token.capture());

        // Unusable until the invitee sets their own password.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token.getValue() + "\",\"newPassword\":\"Ivan2026pass\"}"))
            .andExpect(status().isOk());
        String ivan = login("ivan@example.com", "Ivan2026pass");
        perform(get("/api/v1/auth/me"), ivan)
            .andExpect(jsonPath("$.authorityCode").value("LOZENETS"))
            .andExpect(jsonPath("$.emailVerified").value(true));

        perform(get("/api/v1/authority/members"), lead).andExpect(jsonPath("$", hasSize(2)));
        // A colleague does not add colleagues: one shared password would still open the door.
        perform(invite("someone@example.com", "Някой"), ivan).andExpect(status().isForbidden());
    }

    @Test
    void theAuthorityTakesTheRoleFromSomeoneWhoLeft() throws Exception {
        MvcResult invited = perform(invite("ivan@example.com", "Иван Петров"), lead).andReturn();
        String ivanId = objectMapper.readTree(invited.getResponse().getContentAsString()).get("id").asText();

        perform(delete("/api/v1/authority/members/{id}", ivanId), lead)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.authorityCode").doesNotExist());
        perform(get("/api/v1/authority/members"), lead).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void refusesAnAddressThatHasAnAccountAndAnotherAuthoritysMember() throws Exception {
        perform(invite("sredets@rayon.nabat.test", "Средец"), lead).andExpect(status().isConflict());

        String sredets = login("sredets@rayon.nabat.test", PASSWORD);
        String sredetsId = objectMapper.readTree(perform(get("/api/v1/auth/me"), sredets).andReturn()
            .getResponse().getContentAsString()).get("id").asText();
        perform(delete("/api/v1/authority/members/{id}", sredetsId), lead).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder invite(String email, String name) throws Exception {
        return post("/api/v1/authority/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new AuthorityMembersController.InviteRequest(email, name)));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
