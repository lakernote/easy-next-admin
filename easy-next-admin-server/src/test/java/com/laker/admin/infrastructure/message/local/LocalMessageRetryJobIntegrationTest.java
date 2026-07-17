package com.laker.admin.infrastructure.message.local;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.infrastructure.message.local.mapper.LocalMessageMapper;
import com.laker.admin.module.batch.entity.BatchTask;
import com.laker.admin.module.batch.service.BatchTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LocalMessageRetryJobIntegrationTest {

    @Autowired
    private EasyLocalMessageTemplate localMessageTemplate;

    @Autowired
    private LocalMessageRetryJob localMessageRetryJob;

    @Autowired
    private LocalMessageMapper localMessageMapper;

    @Autowired
    private BatchTaskService batchTaskService;

    @Autowired
    private EasyJsonCodec jsonCodec;

    @Autowired
    private TestRetryOperation testRetryOperation;

    @BeforeEach
    void cleanData() {
        localMessageMapper.delete(Wrappers.<LocalMessage>lambdaQuery()
                .eq(LocalMessage::getOperationName, "test_retry_operation"));
        batchTaskService.remove(Wrappers.<BatchTask>lambdaQuery()
                .eq(BatchTask::getTaskType, "LOCAL_MESSAGE_RETRY"));
        testRetryOperation.localCalls.set(0);
        testRetryOperation.remoteCalls.set(0);
        testRetryOperation.remoteSuccess = true;
        testRetryOperation.remoteStarted = new CountDownLatch(1);
        testRetryOperation.allowRemoteReturn = new CountDownLatch(0);
    }

    @Test
    void templateDispatchesImmediatelyAfterCommit() throws Exception {
        String messageKey = localMessageTemplate.execute(testRetryOperation, Map.of("bizId", "BIZ-001"));

        assertThat(testRetryOperation.remoteStarted.await(2, TimeUnit.SECONDS)).isTrue();
        await(() -> {
            LocalMessage current = localMessageMapper.selectOne(Wrappers.<LocalMessage>lambdaQuery()
                    .eq(LocalMessage::getMessageKey, messageKey));
            return current != null && "SENT".equals(current.getStatus());
        });
        LocalMessage message = localMessageMapper.selectOne(Wrappers.<LocalMessage>lambdaQuery()
                .eq(LocalMessage::getMessageKey, messageKey));
        assertThat(message).isNotNull();
        assertThat(message.getStatus()).isEqualTo("SENT");
        assertThat(message.getNextRetryAt()).isNotNull();
        assertThat(testRetryOperation.localCalls).hasValue(1);
        assertThat(testRetryOperation.remoteCalls).hasValue(1);
    }

    @Test
    void broadcastWorkerDoesNotClaimActiveFastPathLease() throws Exception {
        testRetryOperation.allowRemoteReturn = new CountDownLatch(1);
        String messageKey = localMessageTemplate.execute(testRetryOperation, Map.of("bizId", "BIZ-ACTIVE"));
        assertThat(testRetryOperation.remoteStarted.await(2, TimeUnit.SECONDS)).isTrue();

        localMessageRetryJob.execute(Map.of());

        assertThat(testRetryOperation.remoteCalls).hasValue(1);
        LocalMessage processing = localMessageMapper.selectOne(Wrappers.<LocalMessage>lambdaQuery()
                .eq(LocalMessage::getMessageKey, messageKey));
        assertThat(processing.getStatus()).isEqualTo("PROCESSING");
        assertThat(processing.getLeaseToken()).isNotBlank();

        testRetryOperation.allowRemoteReturn.countDown();
        await(() -> "SENT".equals(localMessageMapper.selectById(processing.getId()).getStatus()));
    }

    @Test
    void broadcastWorkerSendsPendingMessagesWithoutCreatingBatchTask() throws Exception {
        LocalMessage pending = pendingMessage("pending-job-delivery");
        localMessageMapper.insert(pending);

        localMessageRetryJob.execute(Map.of());

        LocalMessage message = localMessageMapper.selectById(pending.getId());
        assertThat(message.getStatus()).isEqualTo("SENT");
        assertThat(message.getSentAt()).isNotNull();
        assertThat(message.getLeaseToken()).isNull();
        assertThat(testRetryOperation.remoteCalls).hasValue(1);
        assertThat(batchTaskService.count(Wrappers.<BatchTask>lambdaQuery()
                .eq(BatchTask::getTaskType, "LOCAL_MESSAGE_RETRY"))).isZero();
    }

    @Test
    void expiredProcessingMessageCanBeTakenOver() throws Exception {
        LocalMessage message = pendingMessage("expired-claim");
        message.setStatus("PROCESSING");
        message.setWorkerId("dead-worker");
        message.setLeaseToken("expired-token");
        message.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        localMessageMapper.insert(message);

        localMessageRetryJob.execute(Map.of());

        LocalMessage updated = localMessageMapper.selectById(message.getId());
        assertThat(updated.getStatus()).isEqualTo("SENT");
        assertThat(updated.getWorkerId()).isNotEqualTo("dead-worker");
        assertThat(updated.getLeaseToken()).isNull();
        assertThat(testRetryOperation.remoteCalls).hasValue(1);
    }

    @Test
    void failedDeliveryReturnsToPendingWithBackoff() throws Exception {
        testRetryOperation.remoteSuccess = false;
        LocalMessage message = pendingMessage("retry-with-backoff");
        localMessageMapper.insert(message);

        localMessageRetryJob.execute(Map.of());

        LocalMessage updated = localMessageMapper.selectById(message.getId());
        assertThat(updated.getStatus()).isEqualTo("PENDING");
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(updated.getLastError()).contains("远程操作返回失败");
        assertThat(updated.getLeaseToken()).isNull();
    }

    private LocalMessage pendingMessage(String messageKey) {
        LocalMessage message = new LocalMessage();
        message.setMessageKey(messageKey);
        message.setOperationName("test_retry_operation");
        message.setPayloadJson(jsonCodec.toJson(Map.of("bizId", "BIZ-" + messageKey)));
        message.setStatus("PENDING");
        message.setRetryCount(0);
        message.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return message;
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @TestConfiguration
    static class TestRetryOperationConfig {
        @Bean
        TestRetryOperation testRetryOperation() {
            return new TestRetryOperation();
        }
    }

    @EasyLocalMessageOperation(name = "test_retry_operation")
    static class TestRetryOperation implements ILocalMessageOperation {
        private final AtomicInteger localCalls = new AtomicInteger();
        private final AtomicInteger remoteCalls = new AtomicInteger();
        private volatile boolean remoteSuccess = true;
        private volatile CountDownLatch remoteStarted = new CountDownLatch(1);
        private volatile CountDownLatch allowRemoteReturn = new CountDownLatch(0);

        @Override
        public boolean localOperation(Map<String, Object> params) {
            localCalls.incrementAndGet();
            return true;
        }

        @Override
        public boolean remoteOperation(Map<String, Object> params) {
            remoteCalls.incrementAndGet();
            remoteStarted.countDown();
            try {
                if (!allowRemoteReturn.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("测试远程调用等待超时");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("测试远程调用被中断", ex);
            }
            return remoteSuccess;
        }
    }
}
