package org.example.nabat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithArchitectureTest {

    @Test
    void detectsVotingModule() {
        assertThat(ApplicationModules.of(NabatApplication.class).getModuleByName("voting")).isPresent();
    }
}
