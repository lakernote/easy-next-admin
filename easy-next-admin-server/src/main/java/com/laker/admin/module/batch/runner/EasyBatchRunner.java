package com.laker.admin.module.batch.runner;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.module.batch.dto.BatchTaskItemSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.enums.BatchTaskStatus;
import com.laker.admin.module.batch.service.BatchTaskService;
import com.laker.admin.module.schedule.core.ScheduleJobRunContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class EasyBatchRunner {
    private static final int MESSAGE_LIMIT = 1000;
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int MAX_PAGE_SIZE = 5000;
    private static final int MAX_PAGE_COUNT = 100000;
    private static final int DEFAULT_ITEM_LEASE_SECONDS = 300;
    private static final int MAX_ITEM_LEASE_SECONDS = 3600;
    private static final Duration DISTRIBUTED_PREPARE_LEASE = Duration.ofMinutes(5);
    private static final long DISTRIBUTED_IDLE_WAIT_MILLIS = 250;

    private final BatchTaskService batchTaskService;
    private final TaskScheduler leaseTaskScheduler;
    private final IEasyLocker easyLocker;
    private final String processWorkerId = "batch:" + EasyIdGenerator.uuid32().substring(0, 12);

    public EasyBatchRunner(BatchTaskService batchTaskService,
                           @Qualifier("easyLockTaskThreadPool") TaskScheduler leaseTaskScheduler,
                           IEasyLocker easyLocker) {
        this.batchTaskService = batchTaskService;
        this.leaseTaskScheduler = leaseTaskScheduler;
        this.easyLocker = easyLocker;
    }

    public <T> Optional<BatchTaskView> run(EasyBatchRunRequest<T> request) throws Exception {
        validate(request);
        if (distributed(batchParams(request))) {
            return Optional.of(runDistributed(request));
        }
        return runPaged(request);
    }

    private <T> BatchTaskView runDistributed(EasyBatchRunRequest<T> request) throws Exception {
        BatchTaskView task = batchTaskService.submitTask(toSubmitRequest(request, List.of()));
        if (terminal(task)) {
            return task;
        }
        task = prepareDistributedTask(request, task);
        if (terminal(task)) {
            return task;
        }

        EasyBatchRunState state = new EasyBatchRunState();
        while (true) {
            if (state.stopRequested()) {
                batchTaskService.skipRunnableItems(task.getId(), state.stopReason());
                return batchTaskService.completeTask(task.getId());
            }
            Optional<EasyBatchClaimedItem> claimed = batchTaskService.claimNextItem(
                    task.getId(), currentWorkerId(), itemLeaseDuration(batchParams(request)));
            if (claimed.isPresent()) {
                ProcessOutcome outcome = processClaimedItem(request, claimed.orElseThrow());
                if (outcome == ProcessOutcome.FAILED
                        && shouldStopAfterFailure(batchParams(request), state.recordFailure())) {
                    state.requestStop(stopReason(batchParams(request)));
                }
                continue;
            }
            BatchTaskView result = batchTaskService.completeTask(task.getId());
            if (terminal(result) || state.stopRequested()) {
                return result;
            }
            Thread.sleep(DISTRIBUTED_IDLE_WAIT_MILLIS);
        }
    }

    private <T> BatchTaskView prepareDistributedTask(EasyBatchRunRequest<T> request,
                                                      BatchTaskView initialTask) throws Exception {
        BatchTaskView task = initialTask;
        String lockKey = "batch:prepare:" + task.getId();
        while (BatchTaskStatus.PENDING.code().equals(task.getStatus())) {
            EasyLocker lock = easyLocker.tryAcquire(lockKey, DISTRIBUTED_PREPARE_LEASE);
            if (lock == null) {
                Thread.sleep(DISTRIBUTED_IDLE_WAIT_MILLIS);
                task = batchTaskService.getTask(task.getId());
                continue;
            }
            try {
                task = batchTaskService.getTask(task.getId());
                if (BatchTaskStatus.PENDING.code().equals(task.getStatus())) {
                    appendDistributedItems(request, task.getId());
                    task = batchTaskService.startTask(task.getId());
                }
            } finally {
                easyLocker.release(lock);
            }
        }
        return task;
    }

    private <T> void appendDistributedItems(EasyBatchRunRequest<T> request, Long taskId) throws Exception {
        readPages(request, items -> batchTaskService.appendItems(taskId, toSubmitItems(items)));
    }

    private <T> void readPages(EasyBatchRunRequest<T> request,
                               PageItemConsumer<T> consumer) throws Exception {
        EasyBatchParams params = batchParams(request);
        int pageSize = pageSize(params);
        String cursor = trimToNull(params.getInitialCursor());
        int pageNumber = 1;
        while (pageNumber <= MAX_PAGE_COUNT) {
            EasyBatchPage<T> page = request.getReader().read(new EasyBatchPageRequest(cursor, pageNumber, pageSize));
            List<EasyBatchItem<T>> items = page == null || page.items() == null ? List.of() : page.items();
            if (!items.isEmpty()) {
                consumer.accept(items);
            }
            if (page == null || !page.hasNext()) {
                return;
            }
            cursor = trimToNull(page.nextCursor());
            pageNumber++;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页批处理读取超过最大页数：" + MAX_PAGE_COUNT);
    }

    private <T> Optional<BatchTaskView> runPaged(EasyBatchRunRequest<T> request) throws Exception {
        EasyBatchParams params = batchParams(request);
        int pageSize = pageSize(params);
        String cursor = trimToNull(params.getInitialCursor());
        int pageNumber = 1;
        boolean readAnyItem = false;
        BatchTaskView task = null;
        EasyBatchRunState state = new EasyBatchRunState();

        while (pageNumber <= MAX_PAGE_COUNT) {
            EasyBatchPage<T> page = request.getReader().read(new EasyBatchPageRequest(cursor, pageNumber, pageSize));
            List<EasyBatchItem<T>> items = page == null || page.items() == null ? List.of() : page.items();
            if (!items.isEmpty()) {
                readAnyItem = true;
                if (task == null) {
                    task = startTask(request, items);
                    if (terminal(task)) {
                        return Optional.of(task);
                    }
                }
                batchTaskService.appendItems(task.getId(), toSubmitItems(items));
                processItems(task.getId(), request, items, state);
                if (state.stopRequested()) {
                    batchTaskService.skipRunnableItems(task.getId(), state.stopReason());
                    break;
                }
            }
            if (page == null || !page.hasNext()) {
                break;
            }
            cursor = trimToNull(page.nextCursor());
            pageNumber++;
        }

        if (pageNumber > MAX_PAGE_COUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页批处理读取超过最大页数：" + MAX_PAGE_COUNT);
        }
        if (!readAnyItem) {
            log.debug("Easy batch skipped, taskType={}, taskName={}, reason=no paged items",
                    request.getTaskType(), request.getTaskName());
            return Optional.empty();
        }
        return Optional.of(batchTaskService.completeTask(task.getId()));
    }

    private <T> BatchTaskView startTask(EasyBatchRunRequest<T> request, List<EasyBatchItem<T>> items) {
        BatchTaskView task = batchTaskService.submitTask(toSubmitRequest(request, items));
        if (terminal(task)) {
            return task;
        }
        return batchTaskService.startTask(task.getId());
    }

    private <T> void processItems(Long taskId, EasyBatchRunRequest<T> request, List<EasyBatchItem<T>> items, EasyBatchRunState state) {
        EasyBatchParams params = batchParams(request);
        Duration leaseDuration = itemLeaseDuration(params);
        String workerId = currentWorkerId();
        // Claim 是执行入口，不能先查询再更新；数据库条件 UPDATE 决定当前 Worker 是否拥有该明细。
        for (EasyBatchItem<T> item : items) {
            if (batchTaskService.shouldStop(taskId)) {
                state.requestStop("任务已请求取消");
                break;
            }
            ProcessOutcome outcome = processItem(taskId, request.getProcessor(), item, workerId, leaseDuration);
            if (outcome == ProcessOutcome.FAILED && shouldStopAfterFailure(params, state.recordFailure())) {
                state.requestStop(stopReason(params));
                break;
            }
        }
    }

    private <T> ProcessOutcome processItem(Long taskId,
                                           EasyBatchItemProcessor<T> processor,
                                           EasyBatchItem<T> item,
                                           String workerId,
                                           Duration leaseDuration) {
        Optional<EasyBatchItemClaim> claimed = batchTaskService
                .claimItem(taskId, item.itemKey(), workerId, leaseDuration);
        if (claimed.isEmpty()) {
            return ProcessOutcome.NOT_OWNED;
        }
        EasyBatchItemClaim claim = claimed.orElseThrow();
        try (LeaseGuard lease = startLeaseGuard(claim, leaseDuration)) {
            EasyBatchItemResult result;
            try {
                result = processor.process(item);
            } catch (Exception ex) {
                return writeFailure(claim, lease, failureMessage(ex));
            }
            if (result == null) {
                return writeFailure(claim, lease, "Processor 未返回处理结果");
            }
            if (!lease.isOwned()) {
                return ProcessOutcome.NOT_OWNED;
            }
            if (result.success()) {
                return batchTaskService.markClaimSuccess(claim, normalizeMessage(result, "处理完成"))
                        ? ProcessOutcome.SUCCESS : ProcessOutcome.NOT_OWNED;
            }
            return writeFailure(claim, lease, normalizeMessage(result, "处理失败"));
        }
    }

    private <T> ProcessOutcome processClaimedItem(EasyBatchRunRequest<T> request,
                                                  EasyBatchClaimedItem claimedItem) {
        EasyBatchItemClaim claim = claimedItem.claim();
        Duration leaseDuration = itemLeaseDuration(batchParams(request));
        try (LeaseGuard lease = startLeaseGuard(claim, leaseDuration)) {
            EasyBatchItemResult result;
            try {
                T data = request.getItemDecoder().decode(claimedItem);
                EasyBatchItem<T> item = new EasyBatchItem<>(claim.itemKey(), claimedItem.itemName(), data,
                        claimedItem.inputJson(), claimedItem.remark());
                result = request.getProcessor().process(item);
            } catch (Exception ex) {
                return writeFailure(claim, lease, failureMessage(ex));
            }
            if (result == null) {
                return writeFailure(claim, lease, "Processor 未返回处理结果");
            }
            if (!lease.isOwned()) {
                return ProcessOutcome.NOT_OWNED;
            }
            if (result.success()) {
                return batchTaskService.markClaimSuccess(claim, normalizeMessage(result, "处理完成"))
                        ? ProcessOutcome.SUCCESS : ProcessOutcome.NOT_OWNED;
            }
            return writeFailure(claim, lease, normalizeMessage(result, "处理失败"));
        }
    }

    private ProcessOutcome writeFailure(EasyBatchItemClaim claim, LeaseGuard lease, String message) {
        if (!lease.isOwned()) {
            return ProcessOutcome.NOT_OWNED;
        }
        return batchTaskService.markClaimFailed(claim, message)
                ? ProcessOutcome.FAILED : ProcessOutcome.NOT_OWNED;
    }

    private LeaseGuard startLeaseGuard(EasyBatchItemClaim claim, Duration leaseDuration) {
        Duration refreshInterval = Duration.ofMillis(Math.max(250, leaseDuration.toMillis() / 3));
        AtomicBoolean owned = new AtomicBoolean(true);
        ScheduledFuture<?> future = leaseTaskScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!batchTaskService.renewClaim(claim, leaseDuration)) {
                    owned.set(false);
                }
            } catch (Exception ex) {
                owned.set(false);
                log.warn("Batch item lease renewal failed, taskId={}, itemKey={}, workerId={}",
                        claim.taskId(), claim.itemKey(), claim.workerId(), ex);
            }
        }, Instant.now().plus(refreshInterval), refreshInterval);
        return new LeaseGuard(owned, future);
    }

    private String currentWorkerId() {
        ScheduleJobRunContext context = ScheduleJobRunContext.current();
        if (context != null && StringUtils.hasText(context.getInstanceId())) {
            return context.getInstanceId();
        }
        return processWorkerId;
    }

    private Duration itemLeaseDuration(EasyBatchParams params) {
        int seconds = params.getItemLeaseSeconds() == null
                ? DEFAULT_ITEM_LEASE_SECONDS : params.getItemLeaseSeconds();
        return Duration.ofSeconds(Math.max(1, Math.min(MAX_ITEM_LEASE_SECONDS, seconds)));
    }

    private enum ProcessOutcome {
        SUCCESS,
        FAILED,
        NOT_OWNED
    }

    private static final class LeaseGuard implements AutoCloseable {
        private final AtomicBoolean owned;
        private final ScheduledFuture<?> renewal;

        private LeaseGuard(AtomicBoolean owned, ScheduledFuture<?> renewal) {
            this.owned = owned;
            this.renewal = renewal;
        }

        boolean isOwned() {
            return owned.get();
        }

        @Override
        public void close() {
            if (renewal != null) {
                renewal.cancel(false);
            }
        }
    }

    private <T> BatchTaskSubmitRequest toSubmitRequest(EasyBatchRunRequest<T> request, List<EasyBatchItem<T>> items) {
        BatchTaskSubmitRequest submitRequest = new BatchTaskSubmitRequest();
        submitRequest.setTaskType(request.getTaskType());
        submitRequest.setTaskName(request.getTaskName());
        submitRequest.setBusinessKey(request.getBusinessKey());
        submitRequest.setRunNo(request.getRunNo());
        submitRequest.setTriggerType(request.getTriggerType());
        submitRequest.setTriggerRefId(request.getTriggerRefId());
        submitRequest.setParamsJson(trimToNull(batchParams(request).getParamsJson()));
        submitRequest.setTraceId(request.getTraceId());
        submitRequest.setRemark(request.getRemark());
        submitRequest.setItems(toSubmitItems(items));
        return submitRequest;
    }

    private <T> List<BatchTaskItemSubmitRequest> toSubmitItems(List<EasyBatchItem<T>> items) {
        return items.stream().map(this::toSubmitItem).toList();
    }

    private <T> BatchTaskItemSubmitRequest toSubmitItem(EasyBatchItem<T> item) {
        BatchTaskItemSubmitRequest request = new BatchTaskItemSubmitRequest();
        request.setItemKey(item.itemKey());
        request.setItemName(item.itemName());
        request.setWorkType(item.data() instanceof EasyBatchPartitionData ? "PARTITION" : "RECORD");
        request.setInputJson(item.inputJson());
        request.setRemark(item.remark());
        return request;
    }

    private <T> void validate(EasyBatchRunRequest<T> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批运行请求不能为空");
        }
        if (!StringUtils.hasText(request.getTaskType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务类型不能为空");
        }
        if (!StringUtils.hasText(request.getTaskName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务名称不能为空");
        }
        if (request.getReader() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理分页 Reader 不能为空");
        }
        if (request.getProcessor() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理 Processor 不能为空");
        }
        EasyBatchParams params = batchParams(request);
        if (params.getPageSize() != null && (params.getPageSize() <= 0 || params.getPageSize() > MAX_PAGE_SIZE)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页大小必须在 1-" + MAX_PAGE_SIZE + " 之间");
        }
        if (failureStrategy(params) == EasyBatchFailureStrategy.STOP_ON_FAILURE_LIMIT && failureLimit(params) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "失败阈值必须大于 0");
        }
        if (distributed(params) && !StringUtils.hasText(request.getBusinessKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分布式批处理必须提供稳定的业务幂等键");
        }
        if (distributed(params) && request.getItemDecoder() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分布式批处理必须提供持久化输入解码器");
        }
        if (distributed(params) && failureStrategy(params) != EasyBatchFailureStrategy.CONTINUE_ON_ERROR) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分布式批处理仅支持继续处理失败项，补跑请创建新的 runNo");
        }
    }

    private boolean terminal(BatchTaskView task) {
        return task != null && BatchTaskStatus.of(task.getStatus()).terminal();
    }

    private EasyBatchParams batchParams(EasyBatchRunRequest<?> request) {
        return request.getBatchParams() == null ? EasyBatchParams.empty() : request.getBatchParams();
    }

    private int pageSize(EasyBatchParams params) {
        return params.getPageSize() == null ? DEFAULT_PAGE_SIZE : params.getPageSize();
    }

    private EasyBatchFailureStrategy failureStrategy(EasyBatchParams params) {
        return EasyBatchFailureStrategy.normalize(params.getFailureStrategy());
    }

    private int failureLimit(EasyBatchParams params) {
        return params.getMaxFailureCount() == null ? 1 : params.getMaxFailureCount();
    }

    private boolean distributed(EasyBatchParams params) {
        return Boolean.TRUE.equals(params.getDistributed());
    }

    private boolean shouldStopAfterFailure(EasyBatchParams params, int failedCount) {
        EasyBatchFailureStrategy strategy = failureStrategy(params);
        if (strategy == EasyBatchFailureStrategy.STOP_ON_ERROR) {
            return true;
        }
        return strategy == EasyBatchFailureStrategy.STOP_ON_FAILURE_LIMIT && failedCount >= failureLimit(params);
    }

    private String stopReason(EasyBatchParams params) {
        EasyBatchFailureStrategy strategy = failureStrategy(params);
        if (strategy == EasyBatchFailureStrategy.STOP_ON_FAILURE_LIMIT) {
            return "失败数量达到阈值：" + failureLimit(params);
        }
        return "失败策略要求遇错停止";
    }

    private String normalizeMessage(EasyBatchItemResult result, String defaultMessage) {
        return limitMessage(StringUtils.hasText(result.message()) ? result.message() : defaultMessage);
    }

    private String failureMessage(Exception ex) {
        String message = ex.getMessage();
        return limitMessage(StringUtils.hasText(message) ? message : ex.getClass().getSimpleName());
    }

    private String limitMessage(String message) {
        if (message == null || message.length() <= MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, MESSAGE_LIMIT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static class EasyBatchRunState {
        private int failedCount;
        private String stopReason;

        int recordFailure() {
            failedCount++;
            return failedCount;
        }

        void requestStop(String reason) {
            this.stopReason = reason;
        }

        boolean stopRequested() {
            return stopReason != null;
        }

        String stopReason() {
            return stopReason;
        }
    }

    @FunctionalInterface
    private interface PageItemConsumer<T> {
        void accept(List<EasyBatchItem<T>> items) throws Exception;
    }
}
