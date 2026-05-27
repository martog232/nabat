package org.example.nabat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NabatApplication {

    public static void main(String[] args) {
        SpringApplication.run(NabatApplication.class, args);
    }

}
