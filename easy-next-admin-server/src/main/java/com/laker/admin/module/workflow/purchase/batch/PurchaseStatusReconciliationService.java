package com.laker.admin.module.workflow.purchase.batch;

import com.laker.admin.infrastructure.json.EasyJsonCodec;
import io.github.lakernote.observability.trace.TraceIdContext;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.entity.BatchTask;
import com.laker.admin.module.batch.enums.BatchTriggerType;
import com.laker.admin.module.batch.runner.EasyBatchBusinessKeys;
import com.laker.admin.module.batch.runner.EasyBatchItem;
import com.laker.admin.module.batch.runner.EasyBatchItemResult;
import com.laker.admin.module.batch.runner.EasyBatchPage;
import com.laker.admin.module.batch.runner.EasyBatchParams;
import com.laker.admin.module.batch.runner.EasyBatchRunRequest;
import com.laker.admin.module.batch.runner.EasyBatchRunner;
import com.laker.admin.module.batch.service.BatchTaskService;
import com.laker.admin.module.workflow.purchase.mapper.BizPurchaseRequestMapper;
import com.laker.admin.module.workflow.purchase.support.PurchaseStatusPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购状态日终对账。
 *
 * <p>Job 只负责唤醒；Reader 一次性登记固定分区；多个实例通过 Batch Claim/Lease 动态领取分区。</p>
 */
@Service
@Slf4j
public class PurchaseStatusReconciliationService {
    public static final String TASK_TYPE = "PURCHASE_STATUS_RECONCILIATION";
    public static final String JOB_CODE = "workflow_purchase_status_reconciliation";
    private static final String TASK_NAME = "采购流程状态日终对账";
    private static final int PARTITION_COUNT = 16;
    private static final int PAGE_SIZE = 200;

    private final EasyBatchRunner batchRunner;
    private final BatchTaskService batchTaskService;
    private final BizPurchaseRequestMapper purchaseMapper;
    private final EasyJsonCodec jsonCodec;
    private final TaskExecutor taskExecutor;

    public PurchaseStatusReconciliationService(EasyBatchRunner batchRunner,
                                               BatchTaskService batchTaskService,
                                               BizPurchaseRequestMapper purchaseMapper,
                                               EasyJsonCodec jsonCodec,
                                               @Qualifier("easyThreadPool") TaskExecutor taskExecutor) {
        this.batchRunner = batchRunner;
        this.batchTaskService = batchTaskService;
        this.purchaseMapper = purchaseMapper;
        this.jsonCodec = jsonCodec;
        this.taskExecutor = taskExecutor;
    }

    public BatchTaskView run(LocalDate businessDate, int runNo, String triggerType, String triggerRefId) throws Exception {
        EasyBatchRunRequest<PurchaseReconciliationPartition> request =
                EasyBatchRunRequest.<PurchaseReconciliationPartition>builder()
                        .taskType(TASK_TYPE)
                        .taskName(TASK_NAME)
                        .businessKey(businessKey(businessDate))
                        .runNo(runNo)
                        .triggerType(triggerType)
                        .triggerRefId(triggerRefId)
                        .traceId(TraceIdContext.currentTraceId())
                        .batchParams(EasyBatchParams.builder()
                                .distributed(true)
                                .pageSize(PARTITION_COUNT)
                                .paramsJson(jsonCodec.toJson(new ReconciliationParams(businessDate, PARTITION_COUNT)))
                                .build())
                        .remark("核对采购业务状态与工作流实例状态，发现漂移后按流程状态修复")
                        .reader(page -> EasyBatchPage.last(partitions(businessDate)))
                        .itemDecoder(item -> jsonCodec.fromJson(item.inputJson(), PurchaseReconciliationPartition.class))
                        .processor(this::processPartition)
                        .build();
        return batchRunner.run(request).orElseThrow();
    }

    public int triggerManual(LocalDate businessDate) {
        int runNo = nextRunNo(businessDate);
        BatchTaskView task = batchTaskService.submitTask(pendingManualTask(businessDate, runNo));
        try {
            taskExecutor.execute(() -> {
                try {
                    run(businessDate, runNo, BatchTriggerType.MANUAL.code(), "batch-console");
                } catch (Exception ex) {
                    batchTaskService.requestCancel(task.getId(), "采购状态对账启动或执行异常");
                    log.error("Purchase reconciliation failed, businessDate={}, runNo={}", businessDate, runNo, ex);
                }
            });
        } catch (RuntimeException ex) {
            batchTaskService.requestCancel(task.getId(), "业务线程池拒绝执行");
            throw ex;
        }
        return runNo;
    }

    private BatchTaskSubmitRequest pendingManualTask(LocalDate businessDate, int runNo) {
        BatchTaskSubmitRequest request = new BatchTaskSubmitRequest();
        request.setTaskType(TASK_TYPE);
        request.setTaskName(TASK_NAME);
        request.setBusinessKey(businessKey(businessDate));
        request.setRunNo(runNo);
        request.setTriggerType(BatchTriggerType.MANUAL.code());
        request.setTriggerRefId("batch-console");
        request.setParamsJson(jsonCodec.toJson(new ReconciliationParams(businessDate, PARTITION_COUNT)));
        request.setTraceId(TraceIdContext.currentTraceId());
        request.setRemark("核对采购业务状态与工作流实例状态，发现漂移后按流程状态修复");
        return request;
    }

    private int nextRunNo(LocalDate businessDate) {
        BatchTask latest = batchTaskService.lambdaQuery()
                .eq(BatchTask::getTaskType, TASK_TYPE)
                .eq(BatchTask::getBusinessKey, businessKey(businessDate))
                .orderByDesc(BatchTask::getRunNo)
                .last("LIMIT 1")
                .one();
        return latest == null || latest.getRunNo() == null ? 1 : latest.getRunNo() + 1;
    }

    private List<EasyBatchItem<PurchaseReconciliationPartition>> partitions(LocalDate businessDate) {
        LocalDateTime startTime = businessDate.atStartOfDay();
        LocalDateTime endTime = businessDate.plusDays(1).atStartOfDay();
        PurchaseReconciliationBounds bounds = purchaseMapper.selectReconciliationBounds(startTime, endTime);
        if (bounds == null || bounds.getMinId() == null || bounds.getMaxId() == null) {
            return List.of();
        }
        long rangeSize = Math.max(1, ((bounds.getMaxId() - bounds.getMinId()) / PARTITION_COUNT) + 1);
        List<EasyBatchItem<PurchaseReconciliationPartition>> items = new ArrayList<>(PARTITION_COUNT);
        long startId = bounds.getMinId();
        for (int index = 0; startId <= bounds.getMaxId() && index < PARTITION_COUNT; index++) {
            long endId = Math.min(bounds.getMaxId(), startId + rangeSize - 1);
            PurchaseReconciliationPartition partition =
                    new PurchaseReconciliationPartition(businessDate, startId, endId);
            String itemKey = "id-range-" + String.format("%02d", index + 1);
            items.add(new EasyBatchItem<>(itemKey, "ID " + startId + "-" + endId, partition,
                    jsonCodec.toJson(partition), "range=" + startId + "-" + endId));
            if (endId == bounds.getMaxId()) {
                break;
            }
            startId = endId + 1;
        }
        return items;
    }

    private EasyBatchItemResult processPartition(EasyBatchItem<PurchaseReconciliationPartition> item) {
        PurchaseReconciliationPartition partition = item.data();
        LocalDateTime startTime = partition.businessDate().atStartOfDay();
        LocalDateTime endTime = partition.businessDate().plusDays(1).atStartOfDay();
        long lastId = partition.startId() - 1;
        int scanned = 0;
        int repaired = 0;
        while (true) {
            List<PurchaseReconciliationCandidate> candidates = purchaseMapper.selectReconciliationCandidates(
                    startTime, endTime, partition.startId(), partition.endId(), lastId, PAGE_SIZE);
            if (candidates.isEmpty()) {
                break;
            }
            for (PurchaseReconciliationCandidate candidate : candidates) {
                scanned++;
                String expectedStatus = PurchaseStatusPolicy.fromWorkflow(candidate.getWorkflowStatus());
                if (expectedStatus != null && !expectedStatus.equals(candidate.getPurchaseStatus())) {
                    repaired += purchaseMapper.updateStatusIfUnchanged(candidate.getPurchaseId(),
                            candidate.getPurchaseStatus(), expectedStatus);
                }
                lastId = candidate.getPurchaseId();
            }
            if (candidates.size() < PAGE_SIZE) {
                break;
            }
        }
        return EasyBatchItemResult.success("扫描 " + scanned + " 条，修复 " + repaired + " 条");
    }

    private String businessKey(LocalDate businessDate) {
        return EasyBatchBusinessKeys.dailyPeriod(TASK_TYPE, businessDate);
    }

    private record ReconciliationParams(LocalDate businessDate, int partitionCount) {
    }
}
