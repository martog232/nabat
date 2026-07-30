package org.example.nabat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the hexagonal layering rules stated in AGENTS.md, <em>within</em> each module.
 *
 * <p>Module boundaries themselves are checked by {@link ModularityTest}: Spring Modulith
 * owns the question of which module may see which. This class owns the orthogonal
 * question of how the layers inside a module relate — domain knows nothing of frameworks,
 * application knows nothing of adapters, inbound adapters know nothing of outbound ones.
 *
 * <h2>Why ArchUnit</h2>
 * The previous version scanned import statements with a regex over four fixed directory
 * paths ({@code domain}, {@code application}, {@code adapter/in}). Those paths stopped
 * existing when the codebase moved from layer-first to feature-first packages: every
 * module now has its own {@code domain} and {@code application}. Expressing that needs a
 * matcher over packages rather than a directory walk, and ArchUnit is already on the test
 * classpath through {@code spring-modulith-test}.
 */
class ArchitectureTest {

    /**
     * Production classes only. Tests deliberately cross layers — a controller slice test
     * names the controller and its use case together — and are not subject to these rules.
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.example.nabat");

    @Test
    @DisplayName("domain does not depend on Spring, JPA, Jackson or Lombok")
    void domainIsFrameworkFree() {
        // package-info carries @NamedInterface, which is Spring Modulith metadata on the
        // package declaration rather than a dependency of any domain type.
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameNotContaining("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "com.fasterxml.jackson..",
                "lombok.."
            );

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("domain does not depend on the application or adapter layers")
    void domainDoesNotDependOnOuterLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.example.nabat..application..",
                "org.example.nabat..adapter.."
            );

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("the application layer does not depend on any adapter")
    void applicationDoesNotDependOnAdapters() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("org.example.nabat..adapter..");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("the application layer does not import servlet or Spring Security web types")
    void applicationDoesNotUseWebOrSecurityFrameworks() {
        // Spring Security's PasswordEncoder is the one deliberate exception: it is an
        // abstraction over hashing with no servlet or filter-chain coupling, and giving it
        // a bespoke port would add indirection for no gain. It lives in
        // org.springframework.security.crypto, which is not listed below.
        ArchRule rule = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.servlet..",
                "org.springframework.web..",
                "org.springframework.security.core..",
                "org.springframework.security.authentication..",
                "org.springframework.security.access.."
            );

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("domain does not throw Spring Security exceptions")
    void domainDoesNotUseSecurityFrameworkExceptions() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security..");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("inbound adapters do not depend on outbound adapters")
    void inboundAdaptersDoNotDependOnOutboundAdapters() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("org.example.nabat..adapter.out..");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("REST controllers depend on in-ports, not on out-ports")
    void controllersDoNotUseOutPorts() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..adapter.in.rest..")
            .should().dependOnClassesThat().resideInAPackage("org.example.nabat..application.port.out..");

        rule.check(CLASSES);
    }
}
