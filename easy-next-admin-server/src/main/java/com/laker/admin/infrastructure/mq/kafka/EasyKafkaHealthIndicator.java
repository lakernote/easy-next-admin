package com.laker.admin.infrastructure.mq.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
public class EasyKafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaAdmin kafkaAdmin;
    private final EasyKafkaConfig easyKafkaConfig;

    public EasyKafkaHealthIndicator(KafkaAdmin kafkaAdmin, EasyKafkaConfig easyKafkaConfig) {
        this.kafkaAdmin = kafkaAdmin;
        this.easyKafkaConfig = easyKafkaConfig;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            builder.up();
            addTopicDetails(adminClient, builder);
            addClusterDetails(adminClient, builder);
        } catch (InterruptedException e) {
            builder.down().withException(e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            builder.down().withException(e);
        }
    }

    private void addTopicDetails(AdminClient adminClient, Health.Builder builder) throws ExecutionException, InterruptedException {
        Set<String> topicNames = Set.of(easyKafkaConfig.getTopic());
        Map<String, KafkaFuture<TopicDescription>> topicNameValues = adminClient.describeTopics(topicNames).topicNameValues();

        for (KafkaFuture<TopicDescription> topic : topicNameValues.values()) {
            TopicDescription topicDescription = topic.get();
            Object[] array = topicDescription.partitions().stream().map(partitionInfo -> {
                Map<String, Object> partition = new LinkedHashMap<>();
                partition.put("partition", partitionInfo.partition());
                partition.put("leader", partitionInfo.leader().host());
                partition.put("replicas", partitionInfo.replicas().stream().map(Node::host).toList());
                partition.put("inSyncReplicas", partitionInfo.isr().stream().map(Node::host).toList());
                return partition;
            }).toArray();

            Map<String, Object> topicDetail = new LinkedHashMap<>();
            topicDetail.put("topic", topicDescription.name());
            topicDetail.put("partitions", array);
            topicDetail.put("internal", topicDescription.isInternal());
            builder.withDetail(topicDescription.name(), topicDetail);
        }
    }

    private void addClusterDetails(AdminClient adminClient, Health.Builder builder) throws ExecutionException, InterruptedException {
        DescribeClusterResult clusterResult = adminClient.describeCluster();
        Collection<Node> nodes = clusterResult.nodes().get();
        String clusterId = clusterResult.clusterId().get();
        Node controller = clusterResult.controller().get();

        builder.withDetail("topics", Set.of(easyKafkaConfig.getTopic()))
                .withDetail("nodes", nodes.stream().map(Node::host).toList())
                .withDetail("clusterId", clusterId)
                .withDetail("controller", controller.host());
    }
}
