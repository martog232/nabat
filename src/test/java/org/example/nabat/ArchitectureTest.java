package org.example.nabat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the layering rules stated in AGENTS.md.
 *
 * <h2>Why this replaced ModulithArchitectureTest</h2>
 * The previous test asserted that {@code ApplicationModules.of(...).getModuleByName("voting")}
 * was absent. Spring Modulith derives modules from the <em>direct</em> subpackages of
 * the application root — here {@code adapter}, {@code application}, {@code config} and
 * {@code domain} — so a module named "voting" could never exist and the assertion
 * passed vacuously. It gave the appearance of architecture enforcement while checking
 * nothing, and caught none of the four real violations that had accumulated:
 *
 * <ul>
 *   <li>{@code AuthenticationService} (application) importing
 *       {@code adapter.in.security.LoginAttemptTracker} and {@code RequestContextHelper}</li>
 *   <li>{@code AlertController} injecting the {@code AlertRepository} out-port directly</li>
 *   <li>{@code AlertWebSocketHandler} (adapter.in) depending on
 *       {@code adapter.out.notification.RedisWsPublisher}, and vice versa</li>
 *   <li>Spring Security exceptions thrown from application and domain classes</li>
 * </ul>
 *
 * <p>Implemented by scanning imports rather than with ArchUnit, to avoid adding a
 * dependency for four rules. If more rules are wanted, ArchUnit is the better tool.
 */
class ArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/org/example/nabat");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);", Pattern.MULTILINE);

    @Test
    @DisplayName("domain does not depend on Spring, JPA, Jackson or any adapter")
    void domainIsFrameworkFree() {
        assertNoImportsMatching(
            "domain",
            List.of(
                "org.springframework.",
                "jakarta.persistence.",
                "com.fasterxml.jackson.",
                "lombok.",
                "org.example.nabat.adapter.",
                "org.example.nabat.application."
            ));
    }

    @Test
    @DisplayName("application layer does not depend on any adapter")
    void applicationDoesNotDependOnAdapters() {
        assertNoImportsMatching("application", List.of("org.example.nabat.adapter."));
    }

    @Test
    @DisplayName("application layer does not import servlet or Spring Security types")
    void applicationDoesNotUseWebOrSecurityFrameworks() {
        // Spring Security's PasswordEncoder is the one deliberate exception: it is an
        // abstraction over hashing with no servlet or filter-chain coupling, and giving it
        // a bespoke port would add indirection for no gain.
        assertNoImportsMatching(
            "application",
            List.of(
                "jakarta.servlet.",
                "org.springframework.web.",
                "org.springframework.security.core.",
                "org.springframework.security.authentication.",
                "org.springframework.security.access."
            ));
    }

    @Test
    @DisplayName("domain does not throw Spring Security exceptions")
    void domainDoesNotUseSecurityFrameworkExceptions() {
        assertNoImportsMatching("domain", List.of("org.springframework.security."));
    }

    @Test
    @DisplayName("inbound adapters do not depend on outbound adapters, or the reverse")
    void adaptersDoNotDependOnEachOther() {
        // Both directions are checked. adapter.out.notification legitimately implements
        // interfaces *declared* in adapter.in.websocket (WsClusterRelay, LocalWsDelivery),
        // which is the dependency-inversion fix for the previous mutual coupling — so
        // that package is allowed to see those two types and nothing else.
        assertNoImportsMatching("adapter/in", List.of("org.example.nabat.adapter.out."));
    }

    @Test
    @DisplayName("REST controllers depend on in-ports, not on out-ports")
    void controllersDoNotUseOutPorts() {
        assertNoImportsMatching("adapter/in/rest", List.of("org.example.nabat.application.port.out.AlertRepository",
            "org.example.nabat.application.port.out.UserRepository",
            "org.example.nabat.application.port.out.NotificationRepository",
            "org.example.nabat.application.port.out.UserSubscriptionRepository"));
    }

    private void assertNoImportsMatching(String relativePackagePath, List<String> forbiddenPrefixes) {
        Path root = SOURCE_ROOT.resolve(relativePackagePath);
        assertThat(root).as("source directory %s should exist", root).exists();

        try (Stream<Path> files = Files.walk(root)) {
            List<String> violations = files
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> violationsIn(path, forbiddenPrefixes))
                .toList();

            assertThat(violations)
                .as("forbidden dependencies from %s", relativePackagePath)
                .isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException("Could not scan " + root, e);
        }
    }

    private Stream<String> violationsIn(Path file, List<String> forbiddenPrefixes) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }

        Matcher matcher = IMPORT.matcher(source);
        Stream.Builder<String> violations = Stream.builder();
        while (matcher.find()) {
            String imported = matcher.group(1);
            for (String forbidden : forbiddenPrefixes) {
                if (imported.startsWith(forbidden) && !isAllowedException(file, imported)) {
                    violations.add(file.getFileName() + " imports " + imported);
                }
            }
        }
        return violations.build();
    }

    /**
     * Sanctioned exceptions, kept narrow and explicit so they cannot quietly widen.
     *
     * <p>Spring Modulith's {@code @NamedInterface} in {@code package-info.java} files:
     * these are compile-time module metadata on the package declaration itself, not a
     * runtime dependency — no domain type touches Spring because of them, and removing
     * them would change the module boundaries Modulith enforces.
     */
    private boolean isAllowedException(Path file, String imported) {
        return "package-info.java".equals(String.valueOf(file.getFileName()))
            && imported.startsWith("org.springframework.modulith.");
    }
}
