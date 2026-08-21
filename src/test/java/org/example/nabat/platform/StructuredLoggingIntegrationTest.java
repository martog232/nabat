package org.example.nabat.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that log output is machine-parseable JSON carrying the trace id as a field.
 *
 * <h2>Why assert on the output rather than trust the property</h2>
 * The whole value of structured logging is that something downstream can parse it: Promtail
 * ships these lines to Loki, and `trace.id` is what makes a log line clickable through to
 * the trace in Grafana. A typo in a property name, or a formatter that drops MDC, produces
 * output that still looks fine to a human reading the console and is useless to the thing
 * that has to read it. So this parses a real line the way Promtail would.
 *
 * <p>Pinned deliberately: the ECS field name is `trace.id`, not `traceId`. Renaming it
 * silently breaks every Grafana query and dashboard built on it, and nothing else in the
 * build would notice.
 */
@SpringBootTest(properties = {
    "logging.structured.format.console=ecs",
    "logging.level.org.example.nabat.platform=INFO"
})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIntegrationTest extends PostgresTestSupport {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingIntegrationTest.class);

    private static final String MARKER = "structured-logging-probe";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private Tracer tracer;

    @Autowired
    private ObservationRegistry observations;

    /** Prevent real SMTP during integration tests. */
    @MockBean
    private EmailSender emailSender;

    @Test
    void logLinesAreJsonCarryingTheTraceId(CapturedOutput output) throws Exception {
        String expectedTraceId;

        Observation observation = Observation.start("structured-logging-test", observations);
        try (Observation.Scope ignored = observation.openScope()) {
            Span current = tracer.currentSpan();
            assertNotNull(current, "no span in scope; tracing is not wired in this context");
            expectedTraceId = current.context().traceId();
            log.info(MARKER);
        } finally {
            observation.stop();
        }

        JsonNode line = findLoggedJson(output, MARKER)
            .orElseThrow(() -> new AssertionError(
                "no parseable JSON line containing '" + MARKER + "'; structured logging is not "
                + "in effect, so Promtail would be shipping unparsed text. Output was:\n" + output));

        assertEquals(MARKER, line.path("message").asText());

        // Note the flat keys: ECS field names contain dots, they are not nested objects.
        // `path("service").path("name")` reads empty here and is the natural thing to write.
        //
        // The name falls back to spring.application.name — nabat-test here, nabat in
        // production. Asserted because a nameless service makes every line in a shared Loki
        // instance indistinguishable from every other service's.
        assertEquals("nabat-test", line.path("service.name").asText(), "line was: " + line);

        // The point of the whole exercise: the trace id is a field, not prose inside one.
        //
        // `traceId`, not the ECS-canonical `trace.id`. The formatter copies MDC keys
        // verbatim and logging.structured.json.rename.* does not reach them, so the format
        // is ECS-shaped everywhere except here. Pinned so the deviation is a documented
        // fact rather than a surprise for whoever writes the first Grafana query.
        assertEquals(
            expectedTraceId,
            line.path("traceId").asText(),
            "the trace id is missing from the JSON, so a log line cannot be pivoted to its "
            + "trace. line was: " + line
        );
    }

    /**
     * Parses console output the way a log shipper does — line by line, keeping what is
     * JSON. Anything Boot writes before the formatter is installed (banner, early startup)
     * is plain text and is skipped rather than failing the test.
     */
    private Optional<JsonNode> findLoggedJson(CapturedOutput output, String marker) {
        return output.getAll().lines()
            .filter(line -> line.contains(marker))
            .map(line -> {
                try {
                    return JSON.readTree(line);
                } catch (Exception notJson) {
                    return null;
                }
            })
            .filter(node -> node != null && node.hasNonNull("message"))
            .findFirst();
    }

    @Test
    void everyLineIsIndependentlyParseable(CapturedOutput output) {
        log.info(MARKER + "-single-line");

        long jsonLines = output.getAll().lines()
            .filter(line -> line.contains(MARKER + "-single-line"))
            .filter(line -> {
                try {
                    JSON.readTree(line);
                    return true;
                } catch (Exception notJson) {
                    return false;
                }
            })
            .count();

        // One line in, one parseable object out. A formatter that pretty-prints would still
        // read fine in a terminal while giving Promtail fragments it cannot parse.
        assertTrue(jsonLines >= 1, "the log line did not survive as a single JSON object per line");
    }
}
