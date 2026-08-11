package org.example.nabat.platform;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins what each Kubernetes probe is allowed to consider.
 *
 * <h2>Why the membership assertions matter more than the status codes</h2>
 * Both probes used to point at the aggregate {@code /actuator/health}, which folds in every
 * indicator including the database. That makes a Postgres outage look like a dead pod:
 * Kubernetes restarts it, the restart does not repair Postgres, and the loop repeats while
 * throwing away whatever the pod could still do. The distinction between the two groups is
 * the entire fix, and it is configuration — nothing in the build would notice if a future
 * edit folded `db` back into liveness, or dropped it from readiness, until an incident.
 *
 * <p>The 200s are asserted too, for a duller reason: these paths are enumerated in
 * SecurityConfig, whose fallback is {@code denyAll()}. Miss one and every probe gets a 401
 * and the pod is killed for a security rule rather than for being unhealthy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class HealthProbesIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpointGroups groups;

    /** Prevent real SMTP during integration tests. */
    @MockBean
    private EmailSender emailSender;

    @Test
    void bothProbeEndpointsAreReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void livenessIgnoresTheDatabase() {
        HealthEndpointGroup liveness = groups.get("liveness");
        assertNotNull(liveness, "no liveness group: management.endpoint.health.probes.enabled is off");

        assertFalse(
            liveness.isMember("db"),
            "liveness includes the database, so a Postgres outage will be reported as a dead "
            + "pod and Kubernetes will restart this process in a loop that cannot fix it"
        );
        assertFalse(
            liveness.isMember("redis"),
            "liveness includes Redis; a restart does not repair Redis either"
        );
    }

    @Test
    void readinessConsidersTheDatabaseButNotRedis() {
        HealthEndpointGroup readiness = groups.get("readiness");
        assertNotNull(readiness, "no readiness group: management.endpoint.health.probes.enabled is off");

        assertTrue(
            readiness.isMember("db"),
            "readiness ignores the database, so a pod that cannot reach Postgres keeps "
            + "receiving requests it can only answer with errors"
        );
        assertFalse(
            readiness.isMember("redis"),
            "readiness includes Redis. Every replica shares one Redis, so this turns a "
            + "partial degradation into every pod leaving the load balancer simultaneously "
            + "— a total outage caused by the health check itself"
        );
    }
}
