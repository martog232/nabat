package org.example.nabat.platform;

import org.example.nabat.shared.UseCase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Use-case beans are picked up by the {@link UseCase} stereotype scan below.
 * Do not declare them as explicit @Bean methods here — that would create
 * duplicate Spring beans for the same interface.
 *
 * <p>The scan is rooted at the application package rather than a single
 * {@code application.service} package: every module now owns its own
 * {@code <module>.application} package, so there is no one location to name.
 * {@code useDefaultFilters = false} keeps this to {@link UseCase} only — without
 * it the scan would also pick up every {@code @Component} in the tree a second
 * time, on top of the application class's own scan.
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(
    basePackages = {"org.example.nabat"},
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = UseCase.class)
)
public class UseCaseConfig {
}
