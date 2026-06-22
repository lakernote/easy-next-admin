package com.laker.admin.infrastructure.mq.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
public class EasyKafkaConsumer {


    @KafkaListener(topics = "${easy.spring.kafka.topic:easynext}", concurrency = "1", groupId = "easynext")
    public void listen(ConsumerRecord<String, String> consumerRecord, Acknowledgment ack) {
        final String format = """
                receive message topic:{}, partition:{}, offset:{}
                key:{}, message:{},
                timestamp:{}, timestampType:{}, headers:{}
                """.trim();
        log.info(format,
                consumerRecord.topic(),
                consumerRecord.partition(), consumerRecord.offset(), consumerRecord.key(), consumerRecord.value(),
                consumerRecord.timestamp(), consumerRecord.timestampType(),
                consumerRecord.headers());
        //手动提交
        //enable.auto.commit参数设置成false。
        ack.acknowledge();
    }

}
