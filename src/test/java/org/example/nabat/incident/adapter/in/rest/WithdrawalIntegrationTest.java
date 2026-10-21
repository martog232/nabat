package org.example.nabat.incident.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.in.rest.AuthResponse;
import org.example.nabat.identity.adapter.in.rest.RegisterRequest;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A reporter takes their report back over HTTP: final, said in the thread, and the authority
 * that had it is told — while nobody else may do it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WithdrawalIntegrationTest extends PostgresTestSupport {

    /** Studentski grad, in STUDENTSKI. */
    private static final double LAT = 42.6515;
    private static final double LON = 23.3455;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserJpaRepository users;
    @Autowired private AlertJpaRepository alerts;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private EmailSender accountMail;
    @MockitoBean private SignalMailer signalMailer;

    private String reporter;
    private String someoneElse;
    private String studentski;
    private UUID report;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM signal_dispatches");
        alerts.deleteAllInBatch();
        users.deleteAll();

        reporter = register("reporter@example.com").accessToken();
        someoneElse = register("other@example.com").accessToken();
        AuthResponse official = register("studentski@example.com");
        jdbc.update("UPDATE users SET role = 'OFFICIAL', authority_code = 'STUDENTSKI' WHERE id = ?", official.user().id());
        studentski = official.accessToken();
        report = createReport();
        awaitStatus("SENT");
    }

    @Test
    void theReporterWithdrawsItAndTheAuthorityIsTold() throws Exception {
        perform(withdraw("Сгреших мястото"), reporter)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.closedBy").value("REPORTER"))
            .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        verify(signalMailer, timeout(10_000)).send(argThat(m ->
            m.kind() == Kind.WITHDRAWN && m.note().contains("Сгреших мястото")));
        perform(get("/api/v1/alerts/{id}/comments", report), reporter)
            .andExpect(jsonPath("$[0].body").value("Оттеглен от подателя: Сгреших мястото"));
        // Off the authority's working list, which shows what is still waiting on it.
        perform(get("/api/v1/alerts/assigned"), studentski).andExpect(jsonPath("$", hasSize(0)));
        // And closed for good.
        perform(withdraw("Пак"), reporter).andExpect(status().isConflict());
    }

    /** A moderator rejects and an authority rejects or forwards; withdrawing is the reporter's. */
    @Test
    void nobodyElseWithdrawsItAndNotWithoutAReason() throws Exception {
        perform(withdraw("Не е мой"), someoneElse).andExpect(status().isForbidden());
        perform(withdraw("Не е наш"), studentski).andExpect(status().isForbidden());
        perform(withdraw("  "), reporter).andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder withdraw(String reason) throws Exception {
        return post("/api/v1/alerts/{id}/withdraw", report)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new WithdrawalController.WithdrawRequest(reason)));
    }

    private AuthResponse register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123", "User " + email))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private UUID createReport() throws Exception {
        MvcResult result = perform(post("/api/v1/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateAlertRequest(
                    "Счупен тротоар", "Пред блок 58", 1008, LAT, LON, null))),
            reporter)
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AlertResponse.class).id();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private void awaitStatus(String expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String actual = null;
        while (System.nanoTime() < deadline) {
            actual = jdbc.queryForObject("SELECT status FROM alerts WHERE id = ?", String.class, report);
            if (expected.equals(actual)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("report is " + actual + ", expected " + expected);
    }
}
