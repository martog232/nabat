package org.example.nabat.adapter.in.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Wraps every public method on {@code @UseCase}-annotated services with a
 * Micrometer {@link Timer}.
 *
 * <p>Metric name: {@code nabat.usecase.duration}
 * <p>Tags:
 * <ul>
 *   <li>{@code usecase} — simple class name of the service, e.g. {@code CreateAlertService}</li>
 *   <li>{@code method}  — method name, e.g. {@code create}</li>
 *   <li>{@code outcome} — {@code success} or {@code error}</li>
 * </ul>
 *
 * <p>Prometheus example:
 * <pre>
 * nabat_usecase_duration_seconds_count{application="nabat",method="create",outcome="success",usecase="CreateAlertService"} 42.0
 * nabat_usecase_duration_seconds_sum{...} 0.123
 * </pre>
 */
@Aspect
@Component
public class UseCaseMetricsAspect {

    private static final String METRIC_NAME = "nabat.usecase.duration";

    private final MeterRegistry meterRegistry;

    public UseCaseMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Intercepts all public methods on classes annotated with {@code @UseCase}.
     */
    @Around("@within(org.example.nabat.application.UseCase)")
    public Object timeUseCase(ProceedingJoinPoint pjp) throws Throwable {
        String useCaseName = pjp.getTarget().getClass().getSimpleName();
        String methodName  = pjp.getSignature().getName();

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            outcome = "error";
            throw t;
        } finally {
            sample.stop(
                Timer.builder(METRIC_NAME)
                    .description("Execution time of @UseCase service methods")
                    .tag("usecase", useCaseName)
                    .tag("method", methodName)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
            );
        }
    }
}

