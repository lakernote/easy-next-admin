package com.laker.observability.autoconfigure;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.integration.kafka.TraceIdProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTraceAutoConfigurationTest {
    @Test
    void addsTracePropagationToListenerContainer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaTraceAutoConfiguration.class,
                        KafkaListenerTraceAutoConfiguration.class))
                .withBean(EasyObservabilityProperties.class, EasyObservabilityProperties::new)
                .run(context -> {
                    TestContainer container = new TestContainer(
                            org.mockito.Mockito.mock(ConsumerFactory.class),
                                    new ContainerProperties("events"));
                    context.getBean(ContainerCustomizer.class).configure(container);
                    assertThat(container.installedRecordInterceptor())
                            .isInstanceOf(com.laker.observability.integration.kafka.TraceIdRecordInterceptor.class);
                });
    }

    @Test
    void appendsProducerInterceptorAndKeepsConfiguredInterceptors() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaProducerTraceAutoConfiguration.class))
                .run(context -> {
                    DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(Map.of(
                            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                            ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, List.of("example.ExistingInterceptor")));
                    context.getBean(DefaultKafkaProducerFactoryCustomizer.class).customize(factory);
                    Object configured = factory.getConfigurationProperties()
                            .get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
                    List<?> interceptors = (List<?>) configured;
                    List<String> names = interceptors.stream().map(String::valueOf).toList();
                    assertThat(names).contains("example.ExistingInterceptor",
                            TraceIdProducerInterceptor.class.getName());
                    factory.destroy();
                });
    }

    private static final class TestContainer extends ConcurrentMessageListenerContainer<Object, Object> {
        private TestContainer(ConsumerFactory<Object, Object> factory, ContainerProperties properties) {
            super(factory, properties);
        }

        private RecordInterceptor<Object, Object> installedRecordInterceptor() {
            return super.getRecordInterceptor();
        }
    }
}
