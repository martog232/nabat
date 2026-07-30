package org.example.nabat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the module boundaries declared by the package structure.
 *
 * <p>Spring Modulith derives one module per direct sub-package of the application
 * root, so {@code org.example.nabat.incident} is a module and everything nested
 * inside it is that module's business. A module's own root package is its API;
 * nested packages are internal unless a {@code package-info.java} marks them with
 * {@code @NamedInterface}.
 *
 * <p>This replaces the earlier arrangement in which the direct sub-packages were
 * {@code adapter}, {@code application}, {@code config} and {@code domain} — layers,
 * not modules — which made {@code ApplicationModules} assertions vacuous.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(NabatApplication.class);

    @Test
    void modulesRespectTheirDeclaredBoundaries() {
        MODULES.verify();
    }
}
