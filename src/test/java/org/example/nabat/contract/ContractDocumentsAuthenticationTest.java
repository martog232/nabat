package org.example.nabat.contract;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spec says which endpoints need a token, and says it about the right ones.
 *
 * <p>It described all 29 paths and marked none of them as authenticated for months. Nothing
 * failed, because nothing checks a spec for what it leaves out: Swagger UI simply offered no
 * Authorize button, nabat-fe's generated types carried no hint that a call needs a session,
 * and any collection imported from the file would have had its {@code Authorization} header
 * added by hand — three places quietly compensating for the same omission.
 *
 * <p>This reads the committed file rather than starting a context, which is why it costs
 * milliseconds and no Docker. That is safe because {@code OpenApiContractTest} already fails
 * when the file and the running application disagree; between them, this holds for what the
 * application serves too.
 *
 * <h2>The list below is the point</h2>
 * It is not a restatement of the spec — it is the second copy that makes disagreement
 * visible. {@code SecurityConfig} permits exactly these six and {@code denyAll()}s the rest;
 * {@code AuthEndpointSecurityTest} pins that the filter chain behaves that way. This pins
 * that the document says the same thing. A new endpoint left undocumented is caught by the
 * global requirement being inherited; a new endpoint wrongly marked public, or one of these
 * six quietly losing its exemption, is caught here.
 */
class ContractDocumentsAuthenticationTest {

    private static final Path SPEC = Path.of("contracts", "openapi.json");

    private static final String SCHEME = "bearerAuth";

    /** The six the filter chain permits, and the only operations allowed to clear the requirement. */
    private static final List<String> PUBLIC_OPERATIONS = List.of(
        "POST /api/v1/auth/register",
        "POST /api/v1/auth/verify",
        "POST /api/v1/auth/forgot-password",
        "POST /api/v1/auth/reset-password",
        "POST /api/v1/auth/login",
        "POST /api/v1/auth/refresh"
    );

    private static JsonNode spec() throws Exception {
        return JsonMapper.builder().build().readTree(Files.readString(SPEC));
    }

    @Test
    void theSpecDeclaresHowTheApiIsAuthenticated() throws Exception {
        JsonNode scheme = spec().path("components").path("securitySchemes").path(SCHEME);

        assertThat(scheme.isObject())
            .as("components.securitySchemes.%s is missing — the spec describes the API "
                + "without saying how to authenticate against it", SCHEME)
            .isTrue();
        assertThat(scheme.path("type").asString()).isEqualTo("http");
        assertThat(scheme.path("scheme").asString()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asString()).isEqualTo("JWT");
    }

    @Test
    void everyEndpointRequiresATokenByDefault() throws Exception {
        JsonNode security = spec().path("security");

        assertThat(security.isArray() && !security.isEmpty())
            .as("the spec carries no document-level security requirement, so every operation "
                + "that does not name one is published as public")
            .isTrue();
        assertThat(security.path(0).has(SCHEME))
            .as("the document-level requirement should name %s", SCHEME)
            .isTrue();
    }

    /**
     * Both directions matter: an operation exempt without being on the list, and a listed one
     * that is no longer exempt. The first publishes a protected endpoint as open; the second
     * makes the client believe login needs a token it can only get by logging in.
     */
    @Test
    void onlyTheAuthEndpointsAreExemptFromIt() throws Exception {
        var exempt = new TreeSet<String>();

        for (Map.Entry<String, JsonNode> path : spec().path("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                JsonNode security = operation.getValue().path("security");
                if (security.isArray() && security.isEmpty()) {
                    exempt.add(operation.getKey().toUpperCase() + " " + path.getKey());
                }
            }
        }

        assertThat(exempt)
            .as("the operations that clear the document-level requirement should be exactly "
                + "the ones SecurityConfig permits")
            .containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS);
    }
}
