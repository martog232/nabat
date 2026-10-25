package org.example.nabat.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every key in {@code application.properties} must still be one the Spring Boot on the
 * classpath binds.
 *
 * <p>A renamed property is the quietest failure an upgrade can produce. Boot moved the
 * Zipkin exporter out of the actuator jar and renamed
 * {@code management.zipkin.tracing.endpoint} to
 * {@code management.tracing.export.zipkin.endpoint}; the old key kept working for as long
 * as the old auto-configuration was on the classpath, and stopped the moment it was not.
 * Nothing logged a word. The exporter fell back to its default, {@code localhost:9411} —
 * inside a container, the container itself — so every span was dropped and Zipkin looked
 * like a system with no traffic, which is exactly how a system with no traffic looks.
 *
 * <p>Boot ships the answer in the jars themselves: each module carries
 * {@code META-INF/spring-configuration-metadata.json}, and a key that has been replaced is
 * listed there with {@code deprecation.level: error} and the name that supersedes it. This
 * reads that, so the next rename is a failing test with the new name in the message rather
 * than a dashboard nobody trusts a month later.
 *
 * <p>Only exact matches are checked. Map-valued keys such as
 * {@code management.metrics.distribution.slo.*} have no metadata entry of their own and are
 * simply not found, which is why this asserts on what the metadata condemns rather than on
 * what it recognises.
 */
class ConfigurationPropertyDeprecationTest {

    private static final String METADATA = "classpath*:META-INF/spring-configuration-metadata.json";

    /**
     * Read off disk, not off the classpath. {@code src/test/resources/application.properties}
     * shadows the real one when tests run, and it is the one without the settings worth
     * checking — a classpath lookup here passed unconditionally, including against the
     * renamed key this test exists for.
     */
    private static final Path MAIN_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path TEST_PROPERTIES = Path.of("src/test/resources/application.properties");

    @Test
    @DisplayName("no configured property has been replaced by the Boot version on the classpath")
    void noConfiguredPropertyIsDeprecatedAtErrorLevel() throws IOException {
        Properties configured = new Properties();
        for (Path file : List.of(MAIN_PROPERTIES, TEST_PROPERTIES)) {
            assertThat(file).as("committed properties file").isRegularFile();
            try (InputStream in = Files.newInputStream(file)) {
                configured.load(in);
            }
        }

        Map<String, String> replacements = replacedPropertyNames();
        assertThat(replacements)
                .as("configuration metadata was found on the classpath — an empty map would "
                        + "make this test pass without checking anything")
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (String name : configured.stringPropertyNames()) {
            String replacement = replacements.get(name);
            if (replacement != null) {
                problems.add(name + " -> " + replacement);
            }
        }

        assertThat(problems)
                .as("properties Boot no longer binds, with the names that replaced them")
                .isEmpty();
    }

    /** Property names marked {@code deprecation.level: error}, mapped to their replacement. */
    private Map<String, String> replacedPropertyNames() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> replaced = new HashMap<>();

        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(METADATA)) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode properties = mapper.readTree(in).path("properties");
                for (JsonNode property : properties) {
                    JsonNode deprecation = property.path("deprecation");
                    if (!"error".equals(deprecation.path("level").asText())) {
                        continue;
                    }
                    String replacement = deprecation.path("replacement").asText("(no replacement named)");
                    replaced.put(property.path("name").asText(), replacement);
                }
            }
        }

        return replaced;
    }
}
