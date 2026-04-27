package org.example.nabat.config;

import org.example.nabat.application.UseCase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Use-case beans are picked up by the {@link UseCase} stereotype scan below.
 * Do not declare them as explicit @Bean methods here — that would create
 * duplicate Spring beans for the same interface.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(
    basePackages = "org.example.nabat.application.service",
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = UseCase.class)
)
public class UseCaseConfig {
}
