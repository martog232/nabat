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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A district official acts on their own district's reports over HTTP, and on nobody else's.
 *
 * <p>Through the real security filter, the real database and the V20 constraints. The
 * official is appointed by writing the row directly: the admin endpoint that does it is
 * covered in {@code UserAdministrationServiceTest}, and writing the row also puts the CHECK
 * constraint in the path. The principal is re-read from the database on every request, so
 * the token issued at registration carries the new role at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OfficialIntegrationTest extends PostgresTestSupport {

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
    private String lozenets;
    private UUID report;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM signal_dispatches");
        alerts.deleteAllInBatch();
        users.deleteAll();

        reporter = register("reporter@example.com").accessToken();
        studentski = appoint(register("studentski@example.com"), "STUDENTSKI");
        lozenets = appoint(register("lozenets@example.com"), "LOZENETS");
        report = createReport();
        awaitStatus(report, "SENT");
    }

    @Test
    void theDistrictAcknowledgesStartsAndResolvesItsReport() throws Exception {
        perform(patch("/api/v1/alerts/{id}/acknowledge", report), studentski)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        perform(patch("/api/v1/alerts/{id}/start-work", report), studentski)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        perform(patch("/api/v1/alerts/{id}/resolve", report), studentski)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.closedBy").value("AUTHORITY"));
    }

    @Test
    void anotherDistrictIsRefusedAndChangesNothing() throws Exception {
        perform(patch("/api/v1/alerts/{id}/acknowledge", report), lozenets).andExpect(status().isForbidden());
        perform(patch("/api/v1/alerts/{id}/reject", report), lozenets).andExpect(status().isForbidden());
        perform(patch("/api/v1/alerts/{id}/resolve", report), lozenets).andExpect(status().isForbidden());

        awaitStatus(report, "SENT");
    }

    @Test
    void theReporterMayResolveButNotReject() throws Exception {
        perform(patch("/api/v1/alerts/{id}/reject", report), reporter).andExpect(status().isForbidden());
        perform(patch("/api/v1/alerts/{id}/resolve", report), reporter)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closedBy").value("REPORTER"));
    }

    /** Each official sees their own district, and a citizen has no district list at all. */
    @Test
    void theWorkingListIsTheOfficialsOwnDistrict() throws Exception {
        perform(get("/api/v1/alerts/assigned"), studentski)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].districtCode").value("STUDENTSKI"));
        perform(get("/api/v1/alerts/assigned"), lozenets)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        perform(get("/api/v1/alerts/assigned"), reporter).andExpect(status().isForbidden());
    }

    /** V20, V28: an official without an authority, or an authority on anyone else, cannot be stored. */
    @Test
    void theDatabaseRefusesAnOfficialWithoutADistrictAndADistrictWithoutAnOfficial() {
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE users SET role = 'OFFICIAL', authority_code = NULL WHERE email = 'reporter@example.com'"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE users SET authority_code = 'LOZENETS' WHERE email = 'reporter@example.com'"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AuthResponse register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123", "User " + email))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String appoint(AuthResponse account, String district) {
        jdbc.update("UPDATE users SET role = 'OFFICIAL', authority_code = ? WHERE id = ?",
            district, account.user().id());
        return account.accessToken();
    }

    private UUID createReport() throws Exception {
        MvcResult result = perform(post("/api/v1/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateAlertRequest(
                    "Счупена шахта", "Пред блок 58", 1008, LAT, LON, null))),
            reporter)
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AlertResponse.class).id();
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    /**
     * Waits for the report to reach {@code status}. Setup waits for SENT so that no test
     * races the dispatch listener, which moves the report on its own thread.
     */
    private void awaitStatus(UUID id, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String actual = null;
        while (System.nanoTime() < deadline) {
            actual = jdbc.queryForObject("SELECT status FROM alerts WHERE id = ?", String.class, id);
            if (expected.equals(actual)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("report is " + actual + ", expected " + expected);
    }
}
