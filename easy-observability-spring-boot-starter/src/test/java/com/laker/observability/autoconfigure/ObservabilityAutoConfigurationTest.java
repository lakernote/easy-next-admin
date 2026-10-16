package com.laker.observability.autoconfigure;

import com.laker.observability.metrics.BusinessMetrics;
import com.laker.observability.trace.TraceSpanAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
            .withPropertyValues("easy.observability.metrics.history.enabled=false")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void createsMetricsAndExplicitTraceAspectByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BusinessMetrics.class);
            assertThat(context).hasSingleBean(TraceSpanAspect.class);
        });
    }

    @Test
    void supportsIndependentMetricsAndTraceSwitches() {
        contextRunner.withPropertyValues(
                "easy.observability.metrics.enabled=false",
                "easy.observability.trace.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(BusinessMetrics.class);
            assertThat(context).doesNotHaveBean(TraceSpanAspect.class);
        });
    }

    @Test
    void masterSwitchDisablesStarterBeans() {
        contextRunner.withPropertyValues("easy.observability.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(BusinessMetrics.class);
            assertThat(context).doesNotHaveBean(TraceSpanAspect.class);
        });
    }

    @Test
    void bridgesFrameworkObservationsIntoRecentOperations() {
        ObservationRegistry registry = ObservationRegistry.create();
        contextRunner.withBean(ObservationRegistry.class, () -> registry).run(context -> {
            Observation.createNotStarted("spring.data.redis", registry)
                    .contextualName("GET")
                    .lowCardinalityKeyValue("db.operation", "GET")
                    .start().stop();

            assertThat(context.getBean(com.laker.observability.trace.TraceHistory.class)
                    .recentOperations(10, null)).singleElement()
                    .satisfies(operation -> assertThat(operation.type()).isEqualTo("REDIS"));
        });
    }
}
