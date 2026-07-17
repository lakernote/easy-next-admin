package com.laker.admin.module.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.module.batch.dto.BatchTaskItemQuery;
import com.laker.admin.module.batch.dto.BatchTaskItemStatusCount;
import com.laker.admin.module.batch.dto.BatchTaskItemSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskItemView;
import com.laker.admin.module.batch.dto.BatchTaskQuery;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTask;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import com.laker.admin.module.batch.enums.BatchTaskItemStatus;
import com.laker.admin.module.batch.enums.BatchTaskStatus;
import com.laker.admin.module.batch.enums.BatchTriggerType;
import com.laker.admin.module.batch.mapper.BatchTaskItemMapper;
import com.laker.admin.module.batch.mapper.BatchTaskMapper;
import com.laker.admin.module.batch.runner.EasyBatchClaimedItem;
import com.laker.admin.module.batch.runner.EasyBatchItemClaim;
import com.laker.admin.module.batch.service.BatchTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BatchTaskServiceImpl extends ServiceImpl<BatchTaskMapper, BatchTask> implements BatchTaskService {
    private final BatchTaskItemMapper itemMapper;

    public BatchTaskServiceImpl(BatchTaskItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskView submitTask(BatchTaskSubmitRequest request) {
        validateSubmitRequest(request);
        String taskType = normalizeCode(request.getTaskType());
        String businessKey = trimToNull(request.getBusinessKey());
        int runNo = normalizeRunNo(request.getRunNo());
        if (StringUtils.hasText(businessKey)) {
            BatchTask existing = lambdaQuery()
                    .eq(BatchTask::getTaskType, taskType)
                    .eq(BatchTask::getBusinessKey, businessKey)
                    .eq(BatchTask::getRunNo, runNo)
                    .one();
            if (existing != null) {
                return BatchTaskView.from(existing);
            }
        }

        List<BatchTaskItemSubmitRequest> items = request.getItems() == null ? List.of() : request.getItems();
        BatchTask task = new BatchTask();
        task.setTaskType(taskType);
        task.setTaskName(request.getTaskName().trim());
        task.setBusinessKey(businessKey);
        task.setRunNo(runNo);
        task.setTriggerType(BatchTriggerType.normalize(request.getTriggerType()));
        task.setTriggerRefId(trimToNull(request.getTriggerRefId()));
        task.setParamsJson(trimToNull(request.getParamsJson()));
        task.setStatus(BatchTaskStatus.PENDING.code());
        task.setTotalCount(resolveTotalCount(request, items));
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setSkippedCount(0);
        task.setCancelRequested(false);
        task.setTraceId(trimToNull(request.getTraceId()));
        task.setRemark(trimToNull(request.getRemark()));
        try {
            save(task);
        } catch (DuplicateKeyException ex) {
            if (StringUtils.hasText(businessKey)) {
                BatchTask concurrentTask = lambdaQuery()
                        .eq(BatchTask::getTaskType, taskType)
                        .eq(BatchTask::getBusinessKey, businessKey)
                        .eq(BatchTask::getRunNo, runNo)
                        .one();
                if (concurrentTask != null) {
                    return BatchTaskView.from(concurrentTask);
                }
            }
            throw ex;
        }

        for (BatchTaskItemSubmitRequest itemRequest : items) {
            itemMapper.insert(toTaskItem(task.getId(), itemRequest));
        }
        return BatchTaskView.from(getById(task.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskView appendItems(Long taskId, List<BatchTaskItemSubmitRequest> items) {
        BatchTask task = requireTask(taskId);
        if (BatchTaskStatus.of(task.getStatus()).terminal()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "终态批处理任务不可再追加工作单元");
        }
        List<BatchTaskItemSubmitRequest> actualItems = items == null ? List.of() : items;
        if (actualItems.isEmpty()) {
            return BatchTaskView.from(updateProgress(taskId, false));
        }
        validateItemRequests(actualItems);
        Set<String> requestKeys = actualItems.stream()
                .map(item -> item.getItemKey().trim())
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> existingKeys = itemMapper.selectList(Wrappers.<BatchTaskItem>lambdaQuery()
                        .eq(BatchTaskItem::getTaskId, taskId)
                        .in(BatchTaskItem::getItemKey, requestKeys))
                .stream()
                .map(BatchTaskItem::getItemKey)
                .collect(Collectors.toSet());
        for (BatchTaskItemSubmitRequest itemRequest : actualItems) {
            String itemKey = itemRequest.getItemKey().trim();
            if (existingKeys.contains(itemKey)) {
                continue;
            }
            try {
                itemMapper.insert(toTaskItem(taskId, itemRequest));
            } catch (DuplicateKeyException ignored) {
                // 其他广播实例已经登记同一 itemKey，唯一索引就是并发幂等边界。
            }
        }
        return BatchTaskView.from(updateProgress(taskId, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskView startTask(Long taskId) {
        BatchTask task = requireTask(taskId);
        BatchTaskStatus status = BatchTaskStatus.of(task.getStatus());
        if (status.terminal()) {
            return BatchTaskView.from(task);
        }
        task.setStatus(BatchTaskStatus.RUNNING.code());
        task.setStartedAt(task.getStartedAt() == null ? LocalDateTime.now() : task.getStartedAt());
        task.setFinishedAt(null);
        updateById(task);
        return BatchTaskView.from(getById(taskId));
    }

    @Override
    public Optional<EasyBatchItemClaim> claimItem(Long taskId,
                                                   String itemKey,
                                                   String workerId,
                                                   Duration leaseDuration) {
        BatchTask task = requireTask(taskId);
        if (BatchTaskStatus.of(task.getStatus()).terminal() || shouldStop(taskId)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(itemKey) || !StringUtils.hasText(workerId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理明细和 Worker 标识不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plus(normalizeLeaseDuration(leaseDuration));
        String leaseToken = EasyIdGenerator.uuid32();
        int updated = itemMapper.update(null, Wrappers.<BatchTaskItem>lambdaUpdate()
                .eq(BatchTaskItem::getTaskId, taskId)
                .eq(BatchTaskItem::getItemKey, itemKey.trim())
                .and(status -> status
                        .eq(BatchTaskItem::getStatus, BatchTaskItemStatus.PENDING.code())
                        .or(expired -> expired
                                .eq(BatchTaskItem::getStatus, BatchTaskItemStatus.RUNNING.code())
                                .and(lease -> lease
                                        .isNull(BatchTaskItem::getLeaseUntil)
                                        .or()
                                        .lt(BatchTaskItem::getLeaseUntil, now))))
                .set(BatchTaskItem::getStatus, BatchTaskItemStatus.RUNNING.code())
                .set(BatchTaskItem::getWorkerId, workerId.trim())
                .set(BatchTaskItem::getLeaseToken, leaseToken)
                .set(BatchTaskItem::getLeaseUntil, leaseUntil)
                .set(BatchTaskItem::getErrorMessage, null)
                .set(BatchTaskItem::getResultMessage, null)
                .set(BatchTaskItem::getStartedAt, now)
                .set(BatchTaskItem::getFinishedAt, null)
                .setSql("attempt_count = attempt_count + 1"));
        if (updated != 1) {
            return Optional.empty();
        }
        BatchTaskItem claimed = itemMapper.selectOne(Wrappers.<BatchTaskItem>lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)
                .eq(BatchTaskItem::getItemKey, itemKey.trim())
                .eq(BatchTaskItem::getLeaseToken, leaseToken));
        if (claimed == null) {
            return Optional.empty();
        }
        return Optional.of(new EasyBatchItemClaim(claimed.getId(), taskId, claimed.getItemKey(),
                claimed.getWorkerId(), leaseToken, claimed.getLeaseUntil()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<EasyBatchClaimedItem> claimNextItem(Long taskId,
                                                        String workerId,
                                                        Duration leaseDuration) {
        BatchTask task = requireTask(taskId);
        if (BatchTaskStatus.of(task.getStatus()).terminal() || shouldStop(taskId)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(workerId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Worker 标识不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        BatchTaskItem item = itemMapper.selectClaimable(taskId, now);
        if (item == null) {
            return Optional.empty();
        }
        String token = EasyIdGenerator.uuid32();
        LocalDateTime leaseUntil = now.plus(normalizeLeaseDuration(leaseDuration));
        item.setStatus(BatchTaskItemStatus.RUNNING.code());
        item.setWorkerId(workerId.trim());
        item.setLeaseToken(token);
        item.setLeaseUntil(leaseUntil);
        item.setErrorMessage(null);
        item.setResultMessage(null);
        item.setStartedAt(now);
        item.setFinishedAt(null);
        item.setAttemptCount(defaultInt(item.getAttemptCount()) + 1);
        if (itemMapper.updateById(item) != 1) {
            return Optional.empty();
        }

        EasyBatchItemClaim claim = new EasyBatchItemClaim(item.getId(), taskId, item.getItemKey(),
                item.getWorkerId(), token, leaseUntil);
        return Optional.of(new EasyBatchClaimedItem(claim, item.getItemName(), item.getInputJson(), item.getRemark()));
    }

    @Override
    public boolean renewClaim(EasyBatchItemClaim claim, Duration leaseDuration) {
        requireClaim(claim);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plus(normalizeLeaseDuration(leaseDuration));
        int updated = itemMapper.update(null, Wrappers.<BatchTaskItem>lambdaUpdate()
                .eq(BatchTaskItem::getId, claim.itemId())
                .eq(BatchTaskItem::getTaskId, claim.taskId())
                .eq(BatchTaskItem::getItemKey, claim.itemKey())
                .eq(BatchTaskItem::getStatus, BatchTaskItemStatus.RUNNING.code())
                .eq(BatchTaskItem::getLeaseToken, claim.leaseToken())
                .ge(BatchTaskItem::getLeaseUntil, now)
                .set(BatchTaskItem::getLeaseUntil, leaseUntil));
        return updated == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markClaimSuccess(EasyBatchItemClaim claim, String resultMessage) {
        return finishClaim(claim, BatchTaskItemStatus.SUCCESS, resultMessage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markClaimFailed(EasyBatchItemClaim claim, String errorMessage) {
        String message = StringUtils.hasText(errorMessage) ? errorMessage.trim() : "处理失败";
        return finishClaim(claim, BatchTaskItemStatus.FAILED, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskView completeTask(Long taskId) {
        BatchTask task = requireTask(taskId);
        if (Boolean.TRUE.equals(task.getCancelRequested()) || BatchTaskStatus.CANCELING.code().equals(task.getStatus())) {
            return BatchTaskView.from(finishCanceledTask(task, task.getErrorMessage()));
        }
        return BatchTaskView.from(updateProgress(taskId, true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskView requestCancel(Long taskId, String reason) {
        BatchTask task = requireTask(taskId);
        BatchTaskStatus status = BatchTaskStatus.of(task.getStatus());
        if (status.terminal()) {
            return BatchTaskView.from(task);
        }

        String cancelReason = StringUtils.hasText(reason) ? reason.trim() : "人工请求取消";
        task.setCancelRequested(true);
        task.setErrorMessage(cancelReason);
        if (status == BatchTaskStatus.PENDING || status == BatchTaskStatus.CANCELING) {
            return BatchTaskView.from(finishCanceledTask(task, cancelReason));
        }

        task.setStatus(BatchTaskStatus.CANCELING.code());
        updateById(task);
        return BatchTaskView.from(getById(taskId));
    }

    @Override
    public boolean shouldStop(Long taskId) {
        BatchTask task = getById(taskId);
        return task != null && (Boolean.TRUE.equals(task.getCancelRequested())
                || BatchTaskStatus.CANCELING.code().equals(task.getStatus())
                || BatchTaskStatus.CANCELED.code().equals(task.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskView skipRunnableItems(Long taskId, String reason) {
        requireTask(taskId);
        String skipReason = StringUtils.hasText(reason) ? reason.trim() : "批处理失败策略中止";
        skipUnfinishedItems(taskId, skipReason, LocalDateTime.now());
        return BatchTaskView.from(updateProgress(taskId, false));
    }

    @Override
    public BatchTaskView getTask(Long taskId) {
        return BatchTaskView.from(requireTask(taskId));
    }

    @Override
    public PageResponse<BatchTaskView> pageTasks(BatchTaskQuery query) {
        BatchTaskQuery actualQuery = query == null ? new BatchTaskQuery() : query;
        Page<BatchTask> page = EasyPageSupport.page(actualQuery.getPage(), actualQuery.getLimit());
        LambdaQueryWrapper<BatchTask> wrapper = new QueryWrapper<BatchTask>().lambda();
        wrapper.eq(StringUtils.hasText(actualQuery.getTaskType()), BatchTask::getTaskType, normalizeCode(actualQuery.getTaskType()))
                .eq(StringUtils.hasText(actualQuery.getTriggerType()), BatchTask::getTriggerType, BatchTriggerType.normalize(actualQuery.getTriggerType()))
                .eq(StringUtils.hasText(actualQuery.getStatus()), BatchTask::getStatus, normalizeCode(actualQuery.getStatus()))
                .and(StringUtils.hasText(actualQuery.getKeyword()), item -> item
                        .like(BatchTask::getTaskName, actualQuery.getKeyword())
                        .or()
                        .like(BatchTask::getTaskType, actualQuery.getKeyword())
                        .or()
                        .like(BatchTask::getBusinessKey, actualQuery.getKeyword()))
                .orderByDesc(BatchTask::getCreateTime);
        Page<BatchTask> result = page(page, wrapper);
        return PageResponse.ok(result.getRecords().stream().map(BatchTaskView::from).toList(), result.getTotal());
    }

    @Override
    public PageResponse<BatchTaskItemView> pageItems(Long taskId, BatchTaskItemQuery query) {
        requireTask(taskId);
        BatchTaskItemQuery actualQuery = query == null ? new BatchTaskItemQuery() : query;
        Page<BatchTaskItem> page = EasyPageSupport.page(actualQuery.getPage(), actualQuery.getLimit());
        LambdaQueryWrapper<BatchTaskItem> wrapper = new QueryWrapper<BatchTaskItem>().lambda();
        wrapper.eq(BatchTaskItem::getTaskId, taskId)
                .eq(StringUtils.hasText(actualQuery.getStatus()), BatchTaskItem::getStatus, normalizeCode(actualQuery.getStatus()))
                .and(StringUtils.hasText(actualQuery.getKeyword()), item -> item
                        .like(BatchTaskItem::getItemKey, actualQuery.getKeyword())
                        .or()
                        .like(BatchTaskItem::getItemName, actualQuery.getKeyword()))
                .orderByAsc(BatchTaskItem::getCreateTime);
        Page<BatchTaskItem> result = itemMapper.selectPage(page, wrapper);
        return PageResponse.ok(result.getRecords().stream().map(BatchTaskItemView::from).toList(), result.getTotal());
    }

    private BatchTask updateProgress(Long taskId, boolean complete) {
        BatchTask task = requireTask(taskId);
        ItemStatusSummary summary = itemStatusSummary(taskId);
        int total = Math.max(defaultInt(task.getTotalCount()), summary.totalCount());
        int completed = summary.completedCount();

        task.setTotalCount(total);
        task.setSuccessCount(summary.successCount());
        task.setFailedCount(summary.failedCount());
        task.setSkippedCount(summary.skippedCount());
        if (complete && completed >= total) {
            task.setFinishedAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(task.getCancelRequested())) {
                task.setStatus(BatchTaskStatus.CANCELED.code());
            } else if (summary.skippedCount() > 0
                    || (summary.failedCount() > 0 && summary.successCount() > 0)) {
                task.setStatus(BatchTaskStatus.PARTIAL_SUCCESS.code());
            } else if (summary.failedCount() > 0) {
                task.setStatus(BatchTaskStatus.FAILED.code());
            } else {
                task.setStatus(BatchTaskStatus.SUCCESS.code());
            }
        }
        updateById(task);
        return getById(taskId);
    }

    private BatchTask finishCanceledTask(BatchTask task, String reason) {
        LocalDateTime now = LocalDateTime.now();
        String cancelReason = StringUtils.hasText(reason) ? reason.trim() : "人工请求取消";
        skipUnfinishedItems(task.getId(), cancelReason, now);

        ItemStatusSummary summary = itemStatusSummary(task.getId());
        int total = Math.max(defaultInt(task.getTotalCount()), summary.totalCount());

        task.setStatus(BatchTaskStatus.CANCELED.code());
        task.setTotalCount(total);
        task.setSuccessCount(summary.successCount());
        task.setFailedCount(summary.failedCount());
        task.setSkippedCount(summary.skippedCount());
        task.setCancelRequested(true);
        task.setErrorMessage(cancelReason);
        task.setResultMessage("任务已取消");
        task.setFinishedAt(now);
        updateById(task);
        return getById(task.getId());
    }

    private void skipUnfinishedItems(Long taskId, String reason, LocalDateTime finishedAt) {
        List<BatchTaskItem> unfinishedItems = itemMapper.selectList(Wrappers.<BatchTaskItem>lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)
                .in(BatchTaskItem::getStatus,
                        BatchTaskItemStatus.PENDING.code(),
                        BatchTaskItemStatus.RUNNING.code()));
        for (BatchTaskItem item : unfinishedItems) {
            item.setStatus(BatchTaskItemStatus.SKIPPED.code());
            item.setErrorMessage(null);
            item.setResultMessage(reason);
            item.setLeaseToken(null);
            item.setLeaseUntil(null);
            item.setFinishedAt(finishedAt);
            itemMapper.updateById(item);
        }
    }

    private ItemStatusSummary itemStatusSummary(Long taskId) {
        int total = 0;
        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (BatchTaskItemStatusCount row : itemMapper.countStatusByTaskId(taskId)) {
            int count = row.getItemCount() == null ? 0 : Math.toIntExact(row.getItemCount());
            total += count;
            String status = row.getStatus();
            if (BatchTaskItemStatus.SUCCESS.code().equals(status)) {
                success = count;
            } else if (BatchTaskItemStatus.FAILED.code().equals(status)) {
                failed = count;
            } else if (BatchTaskItemStatus.SKIPPED.code().equals(status)) {
                skipped = count;
            }
        }
        return new ItemStatusSummary(total, success, failed, skipped);
    }

    private BatchTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理任务 ID 不能为空");
        }
        BatchTask task = getById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批处理任务不存在");
        }
        return task;
    }

    private void requireClaim(EasyBatchItemClaim claim) {
        if (claim == null || claim.itemId() == null || claim.taskId() == null
                || !StringUtils.hasText(claim.itemKey()) || !StringUtils.hasText(claim.leaseToken())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理明细租约凭证不完整");
        }
    }

    private boolean finishClaim(EasyBatchItemClaim claim, BatchTaskItemStatus targetStatus, String message) {
        requireClaim(claim);
        LocalDateTime now = LocalDateTime.now();
        var update = Wrappers.<BatchTaskItem>lambdaUpdate()
                .eq(BatchTaskItem::getId, claim.itemId())
                .eq(BatchTaskItem::getTaskId, claim.taskId())
                .eq(BatchTaskItem::getItemKey, claim.itemKey())
                .eq(BatchTaskItem::getStatus, BatchTaskItemStatus.RUNNING.code())
                .eq(BatchTaskItem::getLeaseToken, claim.leaseToken())
                .ge(BatchTaskItem::getLeaseUntil, now)
                .set(BatchTaskItem::getStatus, targetStatus.code())
                .set(BatchTaskItem::getLeaseToken, null)
                .set(BatchTaskItem::getLeaseUntil, null)
                .set(BatchTaskItem::getFinishedAt, now);
        if (targetStatus == BatchTaskItemStatus.SUCCESS) {
            update.set(BatchTaskItem::getResultMessage, trimToNull(message))
                    .set(BatchTaskItem::getErrorMessage, null);
        } else {
            update.set(BatchTaskItem::getErrorMessage, message)
                    .set(BatchTaskItem::getResultMessage, null);
        }
        int updated = itemMapper.update(null, update);
        if (updated != 1) {
            return false;
        }
        updateProgress(claim.taskId(), false);
        return true;
    }

    private Duration normalizeLeaseDuration(Duration leaseDuration) {
        long seconds = leaseDuration == null ? 300 : leaseDuration.toSeconds();
        return Duration.ofSeconds(Math.max(1, Math.min(3600, seconds)));
    }

    private void validateSubmitRequest(BatchTaskSubmitRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理任务不能为空");
        }
        if (!StringUtils.hasText(request.getTaskType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务类型不能为空");
        }
        if (!StringUtils.hasText(request.getTaskName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务名称不能为空");
        }
        validateItemRequests(request.getItems() == null ? List.of() : request.getItems());
    }

    private void validateItemRequests(List<BatchTaskItemSubmitRequest> items) {
        Set<String> itemKeys = new HashSet<>();
        for (BatchTaskItemSubmitRequest item : items) {
            if (item == null || !StringUtils.hasText(item.getItemKey())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "明细业务键不能为空");
            }
            String key = item.getItemKey().trim();
            if (!itemKeys.add(key)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "明细业务键重复：" + key);
            }
        }
    }

    private int resolveTotalCount(BatchTaskSubmitRequest request, List<BatchTaskItemSubmitRequest> items) {
        if (!items.isEmpty()) {
            return items.size();
        }
        return Math.max(0, defaultInt(request.getTotalCount()));
    }

    private BatchTaskItem toTaskItem(Long taskId, BatchTaskItemSubmitRequest itemRequest) {
        BatchTaskItem item = new BatchTaskItem();
        item.setTaskId(taskId);
        item.setItemKey(itemRequest.getItemKey().trim());
        item.setItemName(trimToNull(itemRequest.getItemName()));
        item.setWorkType(normalizeWorkType(itemRequest.getWorkType()));
        item.setStatus(BatchTaskItemStatus.PENDING.code());
        item.setAttemptCount(0);
        item.setInputJson(trimToNull(itemRequest.getInputJson()));
        item.setRemark(trimToNull(itemRequest.getRemark()));
        return item;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int normalizeRunNo(Integer runNo) {
        if (runNo == null) {
            return 1;
        }
        if (runNo <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "执行序号必须大于 0");
        }
        return runNo;
    }

    private String normalizeWorkType(String workType) {
        String normalized = StringUtils.hasText(workType) ? workType.trim().toUpperCase() : "RECORD";
        if (!"RECORD".equals(normalized) && !"PARTITION".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工作单元类型仅支持 RECORD 或 PARTITION");
        }
        return normalized;
    }

    private record ItemStatusSummary(int totalCount, int successCount, int failedCount, int skippedCount) {
        int completedCount() {
            return successCount + failedCount + skippedCount;
        }
    }
}
