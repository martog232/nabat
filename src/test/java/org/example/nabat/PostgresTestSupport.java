package org.example.nabat;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Canonical Testcontainers base for every integration / persistence test in Nabat.
 *
 * <p>A single {@code postgis/postgis:16-3.4} container is shared across all test
 * classes (static field) which, combined with Spring Boot's application-context
 * cache, keeps the test suite fast: the container starts once per JVM, and each
 * unique Spring context is created once and reused.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // @SpringBootTest integration test
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class MyControllerIntegrationTest extends PostgresTestSupport { ... }
 *
 * // @DataJpaTest slice test
 * @DataJpaTest
 * @AutoConfigureTestDatabase(replace = NONE)
 * class MyRepositoryTest extends PostgresTestSupport { ... }
 * }</pre>
 *
 * <p>Tests are skipped automatically when Docker is not available
 * ({@code disabledWithoutDocker = true}).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestSupport {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("nabat_test")
            .withUsername("nabat")
            .withPassword("nabat");

    /**
     * Overrides the datasource and schema-management properties so every test type
     * (DataJpaTest slice or full SpringBootTest) connects to the shared container.
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",           POSTGRES::getUsername);
        registry.add("spring.datasource.password",           POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Flyway creates/migrates the schema; Hibernate only validates.
        registry.add("spring.flyway.enabled",                () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",        () -> "validate");
        // Suppress SMTP health-check noise in test output.
        registry.add("management.health.mail.enabled",       () -> "false");
    }
}

