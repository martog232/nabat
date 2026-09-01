package org.example.nabat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps {@code contracts/openapi.json} equal to the API this build actually serves.
 *
 * <h2>Why a file in git</h2>
 * nabat-fe writes its TypeScript types by hand, and three times they had drifted from what
 * this service returns — {@code notificationRadiusKm}, {@code enabled} and
 * {@code emailVerified} were in {@code UserResponse} for months while the frontend's
 * {@code User} did not have them. Nothing failed. The settings screen simply could not show a
 * value, and the admin screen could not have been written at all.
 *
 * <p>Drift like that needs somewhere to be visible. The spec in git is that place: a change to
 * any response shape shows up as a diff in a reviewable file, and nabat-fe generates its types
 * from a copy of it, so a rename there becomes a compile error rather than a blank screen.
 *
 * <h2>When this fails</h2>
 * It means the API changed. That is allowed — regenerate the file and commit it with the
 * change, so the diff travels with the code that caused it:
 *
 * <pre>
 *   mvnw test -Dtest=OpenApiContractTest -Dnabat.contract.update=true
 * </pre>
 *
 * <p>Compared as parsed JSON rather than as text, because springdoc has no reason to keep
 * field order stable between runs and a whitespace diff is not a contract change.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest extends PostgresTestSupport {

    private static final Path SPEC = Path.of("contracts", "openapi.json");
    private static final String UPDATE_PROPERTY = "nabat.contract.update";

    @Autowired
    private MockMvc mockMvc;

    /** The context sends a verification email on registration; nothing here registers, but the bean must exist. */
    @MockitoBean
    private EmailSender emailSender;

    @Test
    void theCommittedSpecMatchesTheApiThisBuildServes() throws Exception {
        String served = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        JsonNode servedSpec = mapper.readTree(served);

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, mapper.writeValueAsString(servedSpec) + "\n");
            return;
        }

        assertThat(SPEC)
                .as("%s is missing. Create it with -D%s=true and commit it.", SPEC, UPDATE_PROPERTY)
                .exists();

        JsonNode committedSpec = mapper.readTree(Files.readString(SPEC));

        assertThat(committedSpec)
                .as("""
                    The API this build serves no longer matches %s, so nabat-fe is generating \
                    its types from a stale contract. Regenerate and commit it alongside the \
                    change: mvnw test -Dtest=OpenApiContractTest -D%s=true""",
                    SPEC, UPDATE_PROPERTY)
                .isEqualTo(servedSpec);
    }
}
