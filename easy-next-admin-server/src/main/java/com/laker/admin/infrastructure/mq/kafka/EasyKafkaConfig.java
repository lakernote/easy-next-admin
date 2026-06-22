package com.laker.admin.infrastructure.mq.kafka;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 能力配置。
 *
 * @author easynext
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "easy.spring.kafka")
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
public class EasyKafkaConfig {

    public static final String TOPIC_NAME = "easynext";
    private String bootstrapServers = "localhost:9092";
    private String topic = TOPIC_NAME;

}
