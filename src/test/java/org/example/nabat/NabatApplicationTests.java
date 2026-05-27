package org.example.nabat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class NabatApplicationTests extends PostgresTestSupport {

    @Test
    void contextLoads() {
    }

}
