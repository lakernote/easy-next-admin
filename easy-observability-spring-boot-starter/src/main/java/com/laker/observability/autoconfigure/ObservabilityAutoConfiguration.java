package com.laker.observability.autoconfigure;

import com.laker.observability.metrics.BusinessMetrics;
import com.laker.observability.metrics.MetricCardinalityConfiguration;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.trace.TraceSpanAspect;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.IncidentHistory;
import com.laker.observability.trace.TraceArchive;
import com.laker.observability.trace.TraceContextTaskDecorator;
import com.laker.observability.metrics.history.MetricHistoryCollector;
import com.laker.observability.metrics.history.MetricHistoryStore;
import com.laker.observability.integration.job.JobObservation;
import com.laker.observability.integration.observation.ObservationTraceHandler;
import com.laker.observability.integration.observation.ObservationRegistryPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Configures reusable metrics and trace components for a Spring Boot application. */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EasyObservabilityProperties.class)
@Import(MetricCardinalityConfiguration.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper observabilityObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    BusinessMetrics businessMetrics(MeterRegistry meterRegistry, EasyObservabilityProperties properties) {
        return new BusinessMetrics(meterRegistry, properties.getMetrics().isEnabled());
    }

    @Bean
    @ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    TraceSpanAspect traceSpanAspect(EasyObservabilityProperties properties) {
        return new TraceSpanAspect(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    TraceHistory traceHistory(EasyObservabilityProperties properties) {
        return new TraceHistory(properties.getWeb().getTraceLimit(), properties.getWeb().getOperationLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    IncidentHistory incidentHistory(ObjectMapper objectMapper, EasyObservabilityProperties properties) {
        EasyObservabilityProperties.Trace trace = properties.getTrace();
        return new IncidentHistory(objectMapper, trace.getHistoryFile(), trace.getHistoryLimit(), trace.getHistoryMaxBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    TraceArchive traceArchive(ObjectMapper objectMapper, EasyObservabilityProperties properties) {
        EasyObservabilityProperties.Trace trace = properties.getTrace();
        return new TraceArchive(objectMapper, trace.getArchiveFile(), trace.getHistoryLimit(), trace.getHistoryMaxBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    MetricHistoryStore metricHistoryStore(ObjectMapper objectMapper, EasyObservabilityProperties properties) {
        EasyObservabilityProperties.Metrics.History history = properties.getMetrics().getHistory();
        return new MetricHistoryStore(objectMapper, history.getStorageDir(), history.getRetentionDays(),
                history.getMaxStorageBytes(), history.getMaxSeriesBytes(), history.getMaxSeries(), history.isEnabled());
    }

    @Bean
    @ConditionalOnProperty(prefix = "easy.observability.metrics.history", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    MetricHistoryCollector metricHistoryCollector(MeterRegistry registry, MetricHistoryStore store,
                                                  EasyObservabilityProperties properties) {
        return new MetricHistoryCollector(registry, store, properties.getMetrics().getHistory().getIntervalMs());
    }

    @Bean
    @ConditionalOnMissingBean
    OperationRecorder operationRecorder(MeterRegistry meterRegistry, TraceHistory traceHistory,
                                        IncidentHistory incidentHistory, EasyObservabilityProperties properties) {
        return new OperationRecorder(meterRegistry, traceHistory, incidentHistory, properties);
    }

    @Bean
    @ConditionalOnBean(io.micrometer.observation.ObservationRegistry.class)
    @ConditionalOnMissingBean
    JobObservation jobObservation(io.micrometer.observation.ObservationRegistry registry) {
        return new JobObservation(registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(org.springframework.core.task.TaskDecorator.class)
    TraceContextTaskDecorator traceContextTaskDecorator() {
        return new TraceContextTaskDecorator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    ObservationTraceHandler observationTraceHandler(OperationRecorder recorder, TraceHistory history,
                                                    TraceArchive archive,
                                                    EasyObservabilityProperties properties) {
        return new ObservationTraceHandler(recorder, history, archive, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    static ObservationRegistryPostProcessor easyObservabilityRegistryPostProcessor(
            org.springframework.beans.factory.ObjectProvider<ObservationTraceHandler> handlerProvider) {
        return new ObservationRegistryPostProcessor(handlerProvider);
    }
}
