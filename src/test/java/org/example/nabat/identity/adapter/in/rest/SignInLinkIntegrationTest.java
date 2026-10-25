package org.example.nabat.identity.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.in.ProvisionOfficialsUseCase;
import org.example.nabat.identity.application.port.in.SignInLinkUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The link behind a report mail's button, against the real accounts: it signs in the
 * authority's own official, more than once, and stops working when the account does.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignInLinkIntegrationTest extends PostgresTestSupport {

    @Autowired private ProvisionOfficialsUseCase provisioning;
    @Autowired private SignInLinkUseCase links;
    @Autowired private UserJpaRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM verification_tokens");
        users.deleteAll();
        provisioning.provision("Rayon2026pass");
    }

    @Test
    void signsInTheAuthoritysOwnOfficialAndNotJustOnce() throws Exception {
        String key = links.issueFor("LOZENETS").orElseThrow();

        follow(key)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("lozenets@rayon.nabat.test"))
            .andExpect(jsonPath("$.user.authorityCode").value("LOZENETS"));
        // A second official reading the same mailbox follows the same link.
        follow(key).andExpect(status().isOk());
    }

    /** Only the hash is kept, as for reset tokens: the table alone opens nothing. */
    @Test
    void keepsOnlyTheHashOfTheKey() {
        String key = links.issueFor("CGM").orElseThrow();

        Integer stored = jdbc.queryForObject("SELECT count(*) FROM verification_tokens WHERE id = ?", Integer.class, key);
        assertThat(stored).isZero();
    }

    @Test
    void refusesAnUnknownKey() throws Exception {
        follow("not-a-key").andExpect(status().isUnauthorized());
    }

    /** The link is not a way around switching an account off. */
    @Test
    void stopsWorkingWhenTheAccountIsDisabled() throws Exception {
        String key = links.issueFor("LOZENETS").orElseThrow();
        jdbc.update("UPDATE users SET enabled = false WHERE email = 'lozenets@rayon.nabat.test'");

        follow(key).andExpect(status().isUnauthorized());
    }

    @Test
    void issuesNothingForAnAuthorityWithoutItsOwnAccount() {
        users.deleteAll();

        assertThat(links.issueFor("LOZENETS")).isEmpty();
        assertThat(links.issueFor("NOWHERE")).isEmpty();
    }

    private ResultActions follow(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/link")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + key + "\"}"));
    }
}
