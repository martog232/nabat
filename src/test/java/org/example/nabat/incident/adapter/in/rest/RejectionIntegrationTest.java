package org.example.nabat.incident.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.in.rest.AuthResponse;
import org.example.nabat.identity.adapter.in.rest.RegisterRequest;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A rejection over HTTP, end to end: it says why, the reporter disputes it, and a moderator
 * reopens the report with its authority — each step in the thread, and the thread open again
 * once the report is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RejectionIntegrationTest extends PostgresTestSupport {

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
    private String studentski;
    private String moderator;
    private UUID report;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM signal_dispatches");
        alerts.deleteAllInBatch();
        users.deleteAll();

        reporter = register("reporter@example.com").accessToken();
        studentski = official(register("studentski@example.com"), "STUDENTSKI");
        AuthResponse mod = register("moderator@example.com");
        jdbc.update("UPDATE users SET role = 'MODERATOR' WHERE id = ?", mod.user().id());
        moderator = mod.accessToken();
        report = createReport();
        awaitStatus("SENT");
    }

    @Test
    void rejectedDisputedAndReopened() throws Exception {
        perform(reason(patch("/api/v1/alerts/{id}/reject", report), "Дублира сигнал от вчера"), studentski)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        perform(reason(post("/api/v1/alerts/{id}/rejections/dispute", report), "Дупката още е там"), reporter)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disputeReason").value("Дупката още е там"));
        perform(get("/api/v1/alerts/disputed"), moderator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));

        perform(decision("REOPENED"), moderator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.authorityCode").value("STUDENTSKI"));

        perform(get("/api/v1/alerts/{id}/comments", report), reporter)
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].body").value("Отхвърлен: Дублира сигнал от вчера"))
            .andExpect(jsonPath("$[1].body").value("Оспорвам отхвърлянето: Дупката още е там"))
            .andExpect(jsonPath("$[2].body").value("Модератор отвори сигнала отново след оспорването."));
        perform(get("/api/v1/alerts/{id}/rejections", report), reporter)
            .andExpect(jsonPath("$[0].decision").value("REOPENED"));
        perform(get("/api/v1/alerts/disputed"), moderator).andExpect(jsonPath("$", hasSize(0)));

        // Open again: the authority acts on it, and people may write in its thread.
        perform(patch("/api/v1/alerts/{id}/acknowledge", report), studentski).andExpect(status().isOk());
        perform(post("/api/v1/alerts/{id}/comments", report)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Благодаря\"}"), reporter)
            .andExpect(status().isCreated());
    }

    @Test
    void aRejectionSaysWhyAndIsNotTheReportersToMake() throws Exception {
        perform(reason(patch("/api/v1/alerts/{id}/reject", report), "  "), studentski)
            .andExpect(status().isBadRequest());
        perform(reason(patch("/api/v1/alerts/{id}/reject", report), "Сгреших"), reporter)
            .andExpect(status().isForbidden());
    }

    @Test
    void aDisputeIsTheReportersOnceAndADecisionAModeratorsOnce() throws Exception {
        perform(reason(patch("/api/v1/alerts/{id}/reject", report), "Дублира"), studentski).andExpect(status().isOk());

        perform(reason(post("/api/v1/alerts/{id}/rejections/dispute", report), "Не е"), studentski)
            .andExpect(status().isForbidden());
        perform(reason(post("/api/v1/alerts/{id}/rejections/dispute", report), "Още е там"), reporter)
            .andExpect(status().isOk());
        perform(reason(post("/api/v1/alerts/{id}/rejections/dispute", report), "Пак"), reporter)
            .andExpect(status().isConflict());

        perform(decision("UPHELD"), studentski).andExpect(status().isForbidden());
        perform(decision("UPHELD"), moderator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
        perform(decision("REOPENED"), moderator).andExpect(status().isConflict());
    }

    private MockHttpServletRequestBuilder reason(MockHttpServletRequestBuilder request, String reason) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RejectionController.ReasonRequest(reason)));
    }

    private MockHttpServletRequestBuilder decision(String decision) {
        return post("/api/v1/alerts/{id}/rejections/decision", report)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"" + decision + "\"}");
    }

    private AuthResponse register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123", "User " + email))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String official(AuthResponse account, String authority) {
        jdbc.update("UPDATE users SET role = 'OFFICIAL', authority_code = ? WHERE id = ?",
            authority, account.user().id());
        return account.accessToken();
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
