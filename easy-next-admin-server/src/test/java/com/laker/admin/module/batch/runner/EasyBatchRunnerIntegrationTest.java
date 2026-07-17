package com.laker.admin.module.batch.runner;

import com.laker.admin.module.batch.dto.BatchTaskItemSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import com.laker.admin.module.batch.enums.BatchTriggerType;
import com.laker.admin.module.batch.service.BatchTaskService;
import com.laker.admin.module.batch.service.IBatchTaskItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class EasyBatchRunnerIntegrationTest {

    @Autowired
    private EasyBatchRunner easyBatchRunner;

    @Autowired
    private IBatchTaskItemService itemService;

    @Autowired
    private BatchTaskService batchTaskService;

    @Test
    void runReadsProcessesAndWritesBatchProgress() throws Exception {
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_TEST")
                .taskName("轻量批运行器测试")
                .businessKey("runner-" + UUID.randomUUID())
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-test-job")
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-001", "成功明细", "ok"),
                        EasyBatchItem.of("item-002", "失败明细", "bad")
                )))
                .processor(item -> "ok".equals(item.data())
                        ? EasyBatchItemResult.success("处理完成")
                        : EasyBatchItemResult.failed("业务校验失败"))
                .build();

        Optional<BatchTaskView> result = easyBatchRunner.run(request);

        assertThat(result).isPresent();
        BatchTaskView task = result.orElseThrow();
        assertThat(task.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(task.getTaskType()).isEqualTo("RUNNER_TEST");
        assertThat(task.getTriggerType()).isEqualTo("JOB");
        assertThat(task.getTotalCount()).isEqualTo(2);
        assertThat(task.getSuccessCount()).isEqualTo(1);
        assertThat(task.getFailedCount()).isEqualTo(1);
        assertThat(task.getProgressPercent()).isEqualTo(100);

        List<BatchTaskItem> items = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .orderByAsc(BatchTaskItem::getItemKey)
                .list();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(BatchTaskItem::getItemKey).containsExactly("item-001", "item-002");
        assertThat(items).extracting(BatchTaskItem::getStatus).containsExactly("SUCCESS", "FAILED");
        assertThat(items.get(0).getResultMessage()).isEqualTo("处理完成");
        assertThat(items.get(1).getErrorMessage()).isEqualTo("业务校验失败");
    }

    @Test
    void runMarksNullProcessorResultAsFailure() throws Exception {
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_NULL_RESULT_TEST")
                .taskName("空处理结果测试")
                .businessKey("runner-null-" + UUID.randomUUID())
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-null-test-job")
                .reader(pageRequest -> EasyBatchPage.last(List.of(EasyBatchItem.of("item-null", "空结果明细", "empty"))))
                .processor(item -> null)
                .build();

        BatchTaskView task = easyBatchRunner.run(request).orElseThrow();

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getSuccessCount()).isZero();
        assertThat(task.getFailedCount()).isEqualTo(1);

        BatchTaskItem item = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .eq(BatchTaskItem::getItemKey, "item-null")
                .one();
        assertThat(item.getStatus()).isEqualTo("FAILED");
        assertThat(item.getErrorMessage()).isEqualTo("Processor 未返回处理结果");
    }

    @Test
    void runResumesExistingTaskAndSkipsCompletedItems() throws Exception {
        String businessKey = "runner-resume-" + UUID.randomUUID();
        BatchTaskView existing = batchTaskService.submitTask(submitRequest("RUNNER_RESUME_TEST", "断点续跑测试", businessKey));
        batchTaskService.startTask(existing.getId());
        EasyBatchItemClaim doneClaim = batchTaskService
                .claimItem(existing.getId(), "item-done", "previous-worker", java.time.Duration.ofMinutes(5))
                .orElseThrow();
        batchTaskService.markClaimSuccess(doneClaim, "之前已经完成");
        EasyBatchItemClaim interruptedClaim = batchTaskService
                .claimItem(existing.getId(), "item-running", "interrupted-worker", java.time.Duration.ofMinutes(5))
                .orElseThrow();
        BatchTaskItem interruptedItem = itemService.getById(interruptedClaim.itemId());
        interruptedItem.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        itemService.updateById(interruptedItem);

        List<String> processedKeys = new ArrayList<>();
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_RESUME_TEST")
                .taskName("断点续跑测试")
                .businessKey(businessKey)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-resume-test-job")
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-done", "已完成明细", "done"),
                        EasyBatchItem.of("item-running", "执行中明细", "running"),
                        EasyBatchItem.of("item-pending", "待处理明细", "pending")
                )))
                .processor(item -> {
                    processedKeys.add(item.itemKey());
                    return EasyBatchItemResult.success("恢复处理完成");
                })
                .build();

        BatchTaskView resumed = easyBatchRunner.run(request).orElseThrow();

        assertThat(processedKeys).containsExactly("item-running", "item-pending");
        assertThat(resumed.getStatus()).isEqualTo("SUCCESS");
        assertThat(resumed.getSuccessCount()).isEqualTo(3);
        assertThat(resumed.getFailedCount()).isZero();
        assertThat(resumed.getProgressPercent()).isEqualTo(100);

        List<BatchTaskItem> items = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, resumed.getId())
                .orderByAsc(BatchTaskItem::getItemKey)
                .list();
        assertThat(items).extracting(BatchTaskItem::getItemKey)
                .containsExactly("item-done", "item-pending", "item-running");
        assertThat(items).extracting(BatchTaskItem::getStatus).containsOnly("SUCCESS");
        assertThat(items.stream()
                .filter(item -> "item-done".equals(item.getItemKey()))
                .findFirst()
                .orElseThrow()
                .getResultMessage()).isEqualTo("之前已经完成");
    }

    @Test
    void rerunProcessesSelectedFailedItemsInANewExecution() throws Exception {
        String businessKey = "runner-retry-" + UUID.randomUUID();
        EasyBatchRunRequest<String> firstRun = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_RETRY_TEST")
                .taskName("失败项补跑测试")
                .businessKey(businessKey)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-retry-test-job")
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-ok", "成功明细", "ok"),
                        EasyBatchItem.of("item-fail", "失败明细", "fail")
                )))
                .processor(item -> "ok".equals(item.data())
                        ? EasyBatchItemResult.success("处理完成")
                        : EasyBatchItemResult.failed("首次失败"))
                .build();
        BatchTaskView partial = easyBatchRunner.run(firstRun).orElseThrow();
        assertThat(partial.getStatus()).isEqualTo("PARTIAL_SUCCESS");

        List<String> retryProcessedKeys = new ArrayList<>();
        EasyBatchRunRequest<String> retryRun = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_RETRY_TEST")
                .taskName("失败项补跑测试")
                .businessKey(businessKey)
                .runNo(2)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-retry-test-job")
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-fail", "失败明细", "fail")
                )))
                .processor(item -> {
                    retryProcessedKeys.add(item.itemKey());
                    return EasyBatchItemResult.success("补跑成功");
                })
                .build();

        BatchTaskView retried = easyBatchRunner.run(retryRun).orElseThrow();

        assertThat(retryProcessedKeys).containsExactly("item-fail");
        assertThat(retried.getStatus()).isEqualTo("SUCCESS");
        assertThat(retried.getRunNo()).isEqualTo(2);
        assertThat(retried.getSuccessCount()).isEqualTo(1);
        assertThat(retried.getFailedCount()).isZero();
        assertThat(batchTaskService.getTask(partial.getId()).getStatus()).isEqualTo("PARTIAL_SUCCESS");

        BatchTaskItem retriedItem = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, retried.getId())
                .eq(BatchTaskItem::getItemKey, "item-fail")
                .one();
        assertThat(retriedItem.getAttemptCount()).isEqualTo(1);
        assertThat(retriedItem.getResultMessage()).isEqualTo("补跑成功");
    }

    @Test
    void runPagedReadsByCursorAndPersistsBatchParams() throws Exception {
        List<String> processedKeys = new ArrayList<>();
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_PAGE_TEST")
                .taskName("分页批读取测试")
                .businessKey("runner-page-" + UUID.randomUUID())
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-page-test-job")
                .batchParams(EasyBatchParams.builder()
                        .paramsJson("{\"scope\":\"paged\"}")
                        .initialCursor("0")
                        .pageSize(2)
                        .build())
                .reader(pageRequest -> {
                    assertThat(pageRequest.pageSize()).isEqualTo(2);
                    if (pageRequest.pageNumber() == 1) {
                        assertThat(pageRequest.cursor()).isEqualTo("0");
                        return EasyBatchPage.next(List.of(
                                EasyBatchItem.of("page-001", "分页明细 1", "one"),
                                EasyBatchItem.of("page-002", "分页明细 2", "two")
                        ), "2");
                    }
                    assertThat(pageRequest.cursor()).isEqualTo("2");
                    return EasyBatchPage.last(List.of(EasyBatchItem.of("page-003", "分页明细 3", "three")));
                })
                .processor(item -> {
                    processedKeys.add(item.itemKey());
                    return EasyBatchItemResult.success("分页处理完成");
                })
                .build();

        BatchTaskView task = easyBatchRunner.run(request).orElseThrow();

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getParamsJson()).isEqualTo("{\"scope\":\"paged\"}");
        assertThat(task.getTotalCount()).isEqualTo(3);
        assertThat(task.getSuccessCount()).isEqualTo(3);
        assertThat(processedKeys).containsExactly("page-001", "page-002", "page-003");
    }

    @Test
    void runStopsAndSkipsRemainingItemsWhenFailureStrategyRequiresStop() throws Exception {
        List<String> processedKeys = new ArrayList<>();
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_STOP_ON_ERROR_TEST")
                .taskName("失败策略停止测试")
                .businessKey("runner-stop-" + UUID.randomUUID())
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-stop-test-job")
                .batchParams(EasyBatchParams.builder()
                        .failureStrategy(EasyBatchFailureStrategy.STOP_ON_ERROR)
                        .build())
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-ok", "成功明细", "ok"),
                        EasyBatchItem.of("item-bad", "失败明细", "bad"),
                        EasyBatchItem.of("item-skipped", "未执行明细", "skipped")
                )))
                .processor(item -> {
                    processedKeys.add(item.itemKey());
                    return "bad".equals(item.data())
                            ? EasyBatchItemResult.failed("策略触发失败")
                            : EasyBatchItemResult.success("处理完成");
                })
                .build();

        BatchTaskView task = easyBatchRunner.run(request).orElseThrow();

        assertThat(processedKeys).containsExactly("item-ok", "item-bad");
        assertThat(task.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(task.getSuccessCount()).isEqualTo(1);
        assertThat(task.getFailedCount()).isEqualTo(1);
        assertThat(task.getSkippedCount()).isEqualTo(1);

        BatchTaskItem skipped = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .eq(BatchTaskItem::getItemKey, "item-skipped")
                .one();
        assertThat(skipped.getStatus()).isEqualTo("SKIPPED");
        assertThat(skipped.getResultMessage()).isEqualTo("失败策略要求遇错停止");

        List<String> resumedKeys = new ArrayList<>();
        EasyBatchRunRequest<String> resumedRequest = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_STOP_ON_ERROR_TEST")
                .taskName("失败策略停止测试")
                .businessKey(task.getBusinessKey())
                .runNo(2)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-stop-test-job")
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-bad", "失败明细", "bad"),
                        EasyBatchItem.of("item-skipped", "未执行明细", "skipped")
                )))
                .processor(item -> {
                    resumedKeys.add(item.itemKey());
                    return EasyBatchItemResult.success("补跑完成");
                })
                .build();

        BatchTaskView resumed = easyBatchRunner.run(resumedRequest).orElseThrow();

        assertThat(resumedKeys).containsExactly("item-bad", "item-skipped");
        assertThat(resumed.getStatus()).isEqualTo("SUCCESS");
        assertThat(resumed.getRunNo()).isEqualTo(2);
        assertThat(resumed.getSuccessCount()).isEqualTo(2);
        assertThat(resumed.getSkippedCount()).isZero();
    }

    @Test
    void runProcessesPeriodicPartitionsAsBatchItems() throws Exception {
        String businessKey = EasyBatchBusinessKeys.dailyPeriod("RECON_DAILY", LocalDate.of(2026, 6, 26));
        List<String> processedPartitions = new ArrayList<>();
        EasyBatchRunRequest<EasyBatchPartition> request = EasyBatchRunRequest.<EasyBatchPartition>builder()
                .taskType("RECON_DAILY")
                .taskName("每日对账")
                .businessKey(businessKey)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("recon-daily-job")
                .batchParams(EasyBatchParams.builder()
                        .paramsJson("{\"mode\":\"BUSINESS_PERIOD\",\"period\":\"2026-06-26\"}")
                        .pageSize(10)
                        .distributed(true)
                        .build())
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchPartition.idRange("recon-20260626-0001", 1, 1000).toItem(),
                        EasyBatchPartition.idRange("recon-20260626-0002", 1001, 2000).toItem()
                )))
                .itemDecoder(item -> new EasyBatchPartition(
                        item.claim().itemKey(), item.itemName(), null, null, item.inputJson()))
                .processor(item -> {
                    processedPartitions.add(item.itemKey());
                    return EasyBatchItemResult.success("分区对账完成");
                })
                .build();

        BatchTaskView task = easyBatchRunner.run(request).orElseThrow();

        assertThat(task.getBusinessKey()).isEqualTo("RECON_DAILY:DAY:20260626");
        assertThat(task.getParamsJson()).contains("\"mode\":\"BUSINESS_PERIOD\"");
        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getTotalCount()).isEqualTo(2);
        assertThat(processedPartitions).containsExactly("recon-20260626-0001", "recon-20260626-0002");

        List<BatchTaskItem> items = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .orderByAsc(BatchTaskItem::getItemKey)
                .list();
        assertThat(items).extracting(BatchTaskItem::getItemKey)
                .containsExactly("recon-20260626-0001", "recon-20260626-0002");
        assertThat(items).extracting(BatchTaskItem::getStatus).containsOnly("SUCCESS");
    }

    @Test
    void distributedRunRejectsNodeLocalStopStrategy() {
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("DISTRIBUTED_STOP_STRATEGY_TEST")
                .taskName("分布式失败策略校验")
                .businessKey("distributed-stop-" + UUID.randomUUID())
                .batchParams(EasyBatchParams.builder()
                        .distributed(true)
                        .failureStrategy(EasyBatchFailureStrategy.STOP_ON_ERROR)
                        .build())
                .reader(page -> EasyBatchPage.last(List.of(EasyBatchItem.of("one", "one", "one"))))
                .itemDecoder(EasyBatchClaimedItem::inputJson)
                .processor(item -> EasyBatchItemResult.success("done"))
                .build();

        assertThatThrownBy(() -> easyBatchRunner.run(request))
                .hasMessageContaining("分布式批处理仅支持继续处理失败项");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void runRenewsClaimWhileProcessorIsStillRunning() throws Exception {
        AtomicInteger processCount = new AtomicInteger();
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_LEASE_TEST")
                .taskName("租约续租测试")
                .businessKey("runner-lease-" + UUID.randomUUID())
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-lease-test-job")
                .batchParams(EasyBatchParams.builder().itemLeaseSeconds(1).build())
                .reader(pageRequest -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("slow-item", "慢处理明细", "slow"))))
                .processor(item -> {
                    processCount.incrementAndGet();
                    Thread.sleep(1500);
                    return EasyBatchItemResult.success("慢处理完成");
                })
                .build();

        BatchTaskView task = easyBatchRunner.run(request).orElseThrow();

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(processCount).hasValue(1);
        BatchTaskItem item = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .eq(BatchTaskItem::getItemKey, "slow-item")
                .one();
        assertThat(item.getWorkerId()).isNotBlank();
        assertThat(item.getLeaseToken()).isNull();
        assertThat(item.getLeaseUntil()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void distributedRunWaitsForExpiredLeaseAndTakesOverItem() throws Exception {
        String businessKey = "runner-takeover-" + UUID.randomUUID();
        BatchTaskView existing = batchTaskService.submitTask(submitRequest(
                "RUNNER_TAKEOVER_TEST", "失效租约接管测试", businessKey));
        batchTaskService.startTask(existing.getId());
        batchTaskService.claimItem(existing.getId(), "item-pending", "dead-worker", java.time.Duration.ofSeconds(1))
                .orElseThrow();
        AtomicInteger processCount = new AtomicInteger();
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_TAKEOVER_TEST")
                .taskName("失效租约接管测试")
                .businessKey(businessKey)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-takeover-test-job")
                .batchParams(EasyBatchParams.builder()
                        .distributed(true)
                        .itemLeaseSeconds(1)
                        .build())
                .reader(page -> EasyBatchPage.last(List.of(
                        EasyBatchItem.of("item-done", "已完成明细", "done"),
                        EasyBatchItem.of("item-running", "执行中明细", "running"),
                        EasyBatchItem.of("item-pending", "待接管明细", "pending"))))
                .itemDecoder(EasyBatchClaimedItem::inputJson)
                .processor(item -> {
                    processCount.incrementAndGet();
                    return EasyBatchItemResult.success("接管完成");
                })
                .build();

        BatchTaskView task = easyBatchRunner.run(request).orElseThrow();

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(processCount).hasValue(3);
        BatchTaskItem takenOver = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .eq(BatchTaskItem::getItemKey, "item-pending")
                .one();
        assertThat(takenOver.getWorkerId()).isNotEqualTo("dead-worker");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void distributedRunSharesOneTaskAndClaimsPartitionsAcrossInstances() throws Exception {
        String businessKey = "runner-distributed-" + UUID.randomUUID();
        AtomicInteger readerCalls = new AtomicInteger();
        List<EasyBatchItem<String>> partitions = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> new EasyBatchItem<>(
                        "partition-" + index,
                        "分区 " + index,
                        "data-" + index,
                        "data-" + index,
                        null))
                .toList();
        Map<String, AtomicInteger> processed = new ConcurrentHashMap<>();
        Set<String> workers = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        EasyBatchRunRequest<String> request = EasyBatchRunRequest.<String>builder()
                .taskType("RUNNER_DISTRIBUTED_TEST")
                .taskName("多实例分区测试")
                .businessKey(businessKey)
                .triggerType(BatchTriggerType.JOB.code())
                .triggerRefId("runner-distributed-test-job")
                .batchParams(EasyBatchParams.builder().distributed(true).itemLeaseSeconds(5).build())
                .reader(page -> {
                    readerCalls.incrementAndGet();
                    return EasyBatchPage.last(partitions);
                })
                .itemDecoder(EasyBatchClaimedItem::inputJson)
                .processor(item -> {
                    String worker = com.laker.admin.module.schedule.core.ScheduleJobRunContext.current().getInstanceId();
                    workers.add(worker);
                    processed.computeIfAbsent(item.itemKey(), ignored -> new AtomicInteger()).incrementAndGet();
                    Thread.sleep(20);
                    return EasyBatchItemResult.success("处理完成");
                })
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<BatchTaskView> workerA = executor.submit(() -> runAsWorker("instance-a", request, ready, start));
            Future<BatchTaskView> workerB = executor.submit(() -> runAsWorker("instance-b", request, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            BatchTaskView resultA = workerA.get(15, TimeUnit.SECONDS);
            BatchTaskView resultB = workerB.get(15, TimeUnit.SECONDS);

            assertThat(resultA.getId()).isEqualTo(resultB.getId());
            assertThat(batchTaskService.getTask(resultA.getId()).getStatus()).isEqualTo("SUCCESS");
            assertThat(readerCalls).hasValue(1);
            assertThat(processed).hasSize(20);
            assertThat(processed.values()).allSatisfy(count -> assertThat(count).hasValue(1));
            assertThat(workers).containsExactlyInAnyOrder("instance-a", "instance-b");
        } finally {
            executor.shutdownNow();
        }
    }

    private BatchTaskView runAsWorker(String workerId,
                                      EasyBatchRunRequest<String> request,
                                      CountDownLatch ready,
                                      CountDownLatch start) throws Exception {
        com.laker.admin.module.schedule.core.ScheduleJobRunContext.set(
                com.laker.admin.module.schedule.core.ScheduleJobRunContext.builder()
                        .runId(UUID.randomUUID().toString())
                        .instanceId(workerId)
                        .triggerTime(LocalDateTime.now())
                        .build());
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return easyBatchRunner.run(request).orElseThrow();
        } finally {
            com.laker.admin.module.schedule.core.ScheduleJobRunContext.clear();
        }
    }

    private BatchTaskSubmitRequest submitRequest(String taskType, String taskName, String businessKey) {
        BatchTaskSubmitRequest request = new BatchTaskSubmitRequest();
        request.setTaskType(taskType);
        request.setTaskName(taskName);
        request.setBusinessKey(businessKey);
        request.setTriggerType(BatchTriggerType.JOB.code());
        request.setTriggerRefId("runner-resume-test-job");
        request.setItems(List.of(
                submitItem("item-done", "已完成明细"),
                submitItem("item-running", "执行中明细"),
                submitItem("item-pending", "待处理明细")
        ));
        return request;
    }

    private BatchTaskItemSubmitRequest submitItem(String key, String name) {
        BatchTaskItemSubmitRequest item = new BatchTaskItemSubmitRequest();
        item.setItemKey(key);
        item.setItemName(name);
        item.setInputJson(key);
        return item;
    }
}
