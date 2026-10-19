package org.example.nabat.incident.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.in.rest.AuthResponse;
import org.example.nabat.identity.adapter.in.rest.RegisterRequest;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
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
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A report forwarded over HTTP, end to end: it moves to the new authority, says why in its
 * thread, is mailed there as a second delivery, and the old authority loses it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ForwardIntegrationTest extends PostgresTestSupport {

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
    private String cgm;
    private UUID report;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM signal_dispatches");
        alerts.deleteAllInBatch();
        users.deleteAll();

        reporter = register("reporter@example.com").accessToken();
        studentski = appoint(register("studentski@example.com"), "STUDENTSKI");
        cgm = appoint(register("cgm@example.com"), "CGM");
        report = createReport();
        awaitStatus("SENT");
    }

    @Test
    void theReportGoesToTheNewAuthorityAndIsMailedThere() throws Exception {
        perform(forward("CGM", "Знакът е на ЦГМ"), studentski)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorityCode").value("CGM"))
            .andExpect(jsonPath("$.districtCode").value("STUDENTSKI"));

        ArgumentCaptor<SignalMail> mail = ArgumentCaptor.forClass(SignalMail.class);
        verify(signalMailer, timeout(10_000).times(2)).send(mail.capture());
        SignalMail second = mail.getAllValues().get(1);
        assertThat(second.to()).isEqualTo("cgm@institution.nabat.test");
        assertThat(second.note()).isEqualTo("Препратен към вас от Район Студентски: Знакът е на ЦГМ");
        awaitStatus("SENT");
        assertThat(jdbc.queryForObject(
            "SELECT authority_code FROM signal_dispatches WHERE alert_id = ? AND sequence = 1", String.class, report))
            .isEqualTo("CGM");

        perform(get("/api/v1/alerts/{id}/forwards", report), reporter)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].fromAuthority").value("STUDENTSKI"))
            .andExpect(jsonPath("$[0].reason").value("Знакът е на ЦГМ"));
        perform(get("/api/v1/alerts/{id}/comments", report), reporter)
            .andExpect(jsonPath("$[0].body").value("Препратен към Център за градска мобилност: Знакът е на ЦГМ"))
            .andExpect(jsonPath("$[0].byAuthority").value(true));
    }

    @Test
    void theOldAuthorityLosesItAndTheNewOneHasIt() throws Exception {
        perform(forward("CGM", "Знакът е на ЦГМ"), studentski).andExpect(status().isOk());
        awaitStatus("SENT");

        perform(patch("/api/v1/alerts/{id}/acknowledge", report), studentski).andExpect(status().isForbidden());
        perform(patch("/api/v1/alerts/{id}/acknowledge", report), cgm)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        perform(get("/api/v1/alerts/assigned"), cgm).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/v1/alerts/assigned"), studentski).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void refusesTheReporterAnUnknownAuthorityAndAMissingReason() throws Exception {
        perform(forward("CGM", "Не е на района"), reporter).andExpect(status().isForbidden());
        perform(forward("NOWHERE", "Не е наш"), studentski).andExpect(status().isBadRequest());
        perform(forward("CGM", " "), studentski).andExpect(status().isBadRequest());
        perform(forward("STUDENTSKI", "Наш е"), studentski).andExpect(status().isBadRequest());
    }

    @Test
    void aClosedReportIsNotForwarded() throws Exception {
        perform(patch("/api/v1/alerts/{id}/resolve", report), studentski).andExpect(status().isOk());

        perform(forward("CGM", "Късно"), studentski).andExpect(status().isConflict());
    }

    private MockHttpServletRequestBuilder forward(String authority, String reason) throws Exception {
        return post("/api/v1/alerts/{id}/forward", report)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new ForwardController.ForwardRequest(authority, reason)));
    }

    private AuthResponse register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123", "User " + email))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String appoint(AuthResponse account, String authority) {
        jdbc.update("UPDATE users SET role = 'OFFICIAL', authority_code = ? WHERE id = ?",
            authority, account.user().id());
        return account.accessToken();
    }

    /** A broken pavement, which its category sends to the district — the forward is what moves it. */
    private UUID createReport() throws Exception {
        MvcResult result = perform(post("/api/v1/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateAlertRequest(
                    "Паднал знак", "На ъгъла на тротоара", 1008, LAT, LON, null))),
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
