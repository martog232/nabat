package org.example.nabat.identity.adapter.in.startup;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.in.ProvisionOfficialsUseCase;
import org.example.nabat.identity.application.port.in.ProvisionOfficialsUseCase.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The district accounts against the real districts and the real {@code users} table.
 *
 * <p>What a mocked repository cannot show: that all 24 rows pass the V20 constraint pairing
 * the role with a district, and that the account can actually sign in with its mailbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfficialProvisioningIntegrationTest extends PostgresTestSupport {

    private static final String PASSWORD = "Rayon2026pass";

    @Autowired
    private ProvisionOfficialsUseCase provisioning;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
    }

    @Test
    void everyDistrictGetsOneOfficialWhoCanSignInWithItsMailbox() throws Exception {
        assertEquals(new Result(24, 0, List.of()), provisioning.provision(PASSWORD));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"lozenets@rayon.nabat.test\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("OFFICIAL"))
            .andExpect(jsonPath("$.user.authorityCode").value("LOZENETS"));
    }

    @Test
    void aSecondRunCreatesNothing() {
        provisioning.provision(PASSWORD);

        assertEquals(new Result(0, 24, List.of()), provisioning.provision("Another2026pass"));
        assertEquals(24, userJpaRepository.count());
    }
}
