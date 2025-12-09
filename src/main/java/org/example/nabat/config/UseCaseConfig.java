package org.example.nabat.config;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.UserSubscriptionRepository;
import org.example.nabat.application.service.CreateAlertService;
import org.example.nabat.application.service.GetNearbyAlertsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@ComponentScan(
    basePackages = "org.example.nabat.application.service",
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = UseCase.class)
)
public class UseCaseConfig {

    @Bean
    public CreateAlertUseCase createAlertUseCase(
        AlertRepository alertRepository,
        AlertNotificationPort notificationPort,
        UserSubscriptionRepository subscriptionRepository
    ) {
        return new CreateAlertService(alertRepository, notificationPort, subscriptionRepository);
    }

    @Bean
    public GetNearbyAlertsUseCase getNearbyAlertsUseCase(AlertRepository alertRepository) {
        return new GetNearbyAlertsService(alertRepository);
    }
}
