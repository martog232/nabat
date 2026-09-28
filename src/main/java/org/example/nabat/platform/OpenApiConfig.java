package org.example.nabat.platform;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares how the API is authenticated, so that the contract says so.
 *
 * <p>The generated spec described every path and marked none of them as needing a token.
 * Everything downstream inherited that silence: Swagger UI offered no Authorize button, the
 * types generated in nabat-fe carried no hint that a call needs a session, and any collection
 * imported from the spec would have had its {@code Authorization} header added by hand — each
 * of them papering over the same gap rather than surfacing it.
 *
 * <p><b>The requirement is global and the exceptions are explicit</b>, which is the same
 * direction {@code SecurityConfig} enumerates in and for the same reason. Its filter chain
 * ends in {@code denyAll()} so that a route nobody classified is private by default; a
 * documented default of "open, unless a method says otherwise" would describe the opposite
 * policy, and a new endpoint added without annotations would be published as public while
 * actually answering 401. Six {@code POST /api/v1/auth/*} methods and the two public reads —
 * {@code GET /api/v1/alerts/ranked} and {@code GET /api/v1/districts} — clear the requirement with
 * an empty {@code @SecurityRequirements}, and they are the same eight the filter chain
 * permits — {@code ContractDocumentsAuthenticationTest} is what keeps the two lists from
 * drifting.
 *
 * <p>Note what this does and does not claim. It says a token is required, not that the caller
 * is authorised: {@code /api/v1/admin/**} additionally needs a role, which is enforced by
 * {@code @PreAuthorize} and re-checked against the actor's current row. OpenAPI has no way to
 * express that second gate, so the spec marks those paths exactly as it marks the rest.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Nabat API",
        version = "v1",
        description = """
            Crowd-sourced safety alerts. All endpoints require a bearer access token \
            except the six POST /api/v1/auth/* routes that issue or reset one."""
    ),
    security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
)
@SecurityScheme(
    name = OpenApiConfig.BEARER_SCHEME,
    type = SecuritySchemeType.HTTP,
    in = SecuritySchemeIn.HEADER,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = """
        The access token from POST /api/v1/auth/login or /auth/refresh. Refresh tokens are \
        rejected here: they carry a different token type and are only accepted by /auth/refresh."""
)
public class OpenApiConfig {

    /** Referenced by the annotations above and by the auth endpoints that clear the requirement. */
    public static final String BEARER_SCHEME = "bearerAuth";
}
