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
import com.laker.admin.module.batch.dto.BatchTaskItemQuery;
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
import com.laker.admin.module.batch.service.BatchTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        if (StringUtils.hasText(businessKey)) {
            BatchTask existing = lambdaQuery()
                    .eq(BatchTask::getTaskType, taskType)
                    .eq(BatchTask::getBusinessKey, businessKey)
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
        task.setTriggerType(BatchTriggerType.normalize(request.getTriggerType()));
        task.setTriggerRefId(trimToNull(request.getTriggerRefId()));
        task.setStatus(BatchTaskStatus.PENDING.code());
        task.setTotalCount(resolveTotalCount(request, items));
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setSkippedCount(0);
        task.setProgressPercent(0);
        task.setCancelRequested(false);
        task.setTraceId(trimToNull(request.getTraceId()));
        task.setRemark(trimToNull(request.getRemark()));
        save(task);

        for (BatchTaskItemSubmitRequest itemRequest : items) {
            BatchTaskItem item = new BatchTaskItem();
            item.setTaskId(task.getId());
            item.setItemKey(itemRequest.getItemKey().trim());
            item.setItemName(trimToNull(itemRequest.getItemName()));
            item.setStatus(BatchTaskItemStatus.PENDING.code());
            item.setRetryCount(0);
            item.setPayload(trimToNull(itemRequest.getPayload()));
            item.setRemark(trimToNull(itemRequest.getRemark()));
            itemMapper.insert(item);
        }
        return BatchTaskView.from(getById(task.getId()));
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
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskItemView markItemSuccess(Long taskId, String itemKey, String resultMessage) {
        BatchTaskItem item = requireItem(taskId, itemKey);
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(BatchTaskItemStatus.SUCCESS.code());
        item.setResultMessage(trimToNull(resultMessage));
        item.setErrorMessage(null);
        item.setStartedAt(item.getStartedAt() == null ? now : item.getStartedAt());
        item.setFinishedAt(now);
        itemMapper.updateById(item);
        updateProgress(taskId, false);
        return BatchTaskItemView.from(itemMapper.selectById(item.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTaskItemView markItemFailed(Long taskId, String itemKey, String errorMessage) {
        BatchTaskItem item = requireItem(taskId, itemKey);
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(BatchTaskItemStatus.FAILED.code());
        item.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage.trim() : "处理失败");
        item.setStartedAt(item.getStartedAt() == null ? now : item.getStartedAt());
        item.setFinishedAt(now);
        itemMapper.updateById(item);
        updateProgress(taskId, false);
        return BatchTaskItemView.from(itemMapper.selectById(item.getId()));
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
    public BatchTaskView retryFailedItems(Long taskId) {
        requireTask(taskId);
        List<BatchTaskItem> failedItems = itemMapper.selectList(Wrappers.<BatchTaskItem>lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)
                .eq(BatchTaskItem::getStatus, BatchTaskItemStatus.FAILED.code()));
        LocalDateTime now = LocalDateTime.now();
        for (BatchTaskItem item : failedItems) {
            item.setStatus(BatchTaskItemStatus.PENDING.code());
            item.setRetryCount((item.getRetryCount() == null ? 0 : item.getRetryCount()) + 1);
            item.setErrorMessage(null);
            item.setResultMessage(null);
            item.setStartedAt(null);
            item.setFinishedAt(null);
            item.setUpdateTime(now);
            itemMapper.updateById(item);
        }
        BatchTask task = updateProgress(taskId, false);
        task.setStatus(BatchTaskStatus.PENDING.code());
        task.setCancelRequested(false);
        task.setErrorMessage(null);
        task.setFinishedAt(null);
        updateById(task);
        return BatchTaskView.from(getById(taskId));
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
        long success = countItems(taskId, BatchTaskItemStatus.SUCCESS);
        long failed = countItems(taskId, BatchTaskItemStatus.FAILED);
        long skipped = countItems(taskId, BatchTaskItemStatus.SKIPPED);
        int total = Math.max(defaultInt(task.getTotalCount()), itemCount(taskId));
        int completed = Math.toIntExact(success + failed + skipped);

        task.setTotalCount(total);
        task.setSuccessCount(Math.toIntExact(success));
        task.setFailedCount(Math.toIntExact(failed));
        task.setSkippedCount(Math.toIntExact(skipped));
        task.setProgressPercent(total <= 0 ? 0 : Math.min(100, (completed * 100) / total));
        if (complete) {
            task.setFinishedAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(task.getCancelRequested())) {
                task.setStatus(BatchTaskStatus.CANCELED.code());
            } else if (failed > 0 && success + skipped > 0) {
                task.setStatus(BatchTaskStatus.PARTIAL_SUCCESS.code());
            } else if (failed > 0) {
                task.setStatus(BatchTaskStatus.FAILED.code());
            } else {
                task.setStatus(BatchTaskStatus.SUCCESS.code());
            }
            task.setProgressPercent(100);
        }
        updateById(task);
        return getById(taskId);
    }

    private BatchTask finishCanceledTask(BatchTask task, String reason) {
        LocalDateTime now = LocalDateTime.now();
        String cancelReason = StringUtils.hasText(reason) ? reason.trim() : "人工请求取消";
        skipUnfinishedItems(task.getId(), cancelReason, now);

        long success = countItems(task.getId(), BatchTaskItemStatus.SUCCESS);
        long failed = countItems(task.getId(), BatchTaskItemStatus.FAILED);
        long skipped = countItems(task.getId(), BatchTaskItemStatus.SKIPPED);
        int total = Math.max(defaultInt(task.getTotalCount()), itemCount(task.getId()));

        task.setStatus(BatchTaskStatus.CANCELED.code());
        task.setTotalCount(total);
        task.setSuccessCount(Math.toIntExact(success));
        task.setFailedCount(Math.toIntExact(failed));
        task.setSkippedCount(Math.toIntExact(skipped));
        task.setProgressPercent(100);
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
                        BatchTaskItemStatus.RUNNING.code(),
                        BatchTaskItemStatus.RETRYING.code()));
        for (BatchTaskItem item : unfinishedItems) {
            item.setStatus(BatchTaskItemStatus.SKIPPED.code());
            item.setErrorMessage(null);
            item.setResultMessage(reason);
            item.setFinishedAt(finishedAt);
            itemMapper.updateById(item);
        }
    }

    private long countItems(Long taskId, BatchTaskItemStatus status) {
        return itemMapper.selectCount(Wrappers.<BatchTaskItem>lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)
                .eq(BatchTaskItem::getStatus, status.code()));
    }

    private int itemCount(Long taskId) {
        return Math.toIntExact(itemMapper.selectCount(Wrappers.<BatchTaskItem>lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)));
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

    private BatchTaskItem requireItem(Long taskId, String itemKey) {
        if (!StringUtils.hasText(itemKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理明细业务键不能为空");
        }
        BatchTaskItem item = itemMapper.selectOne(Wrappers.<BatchTaskItem>lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)
                .eq(BatchTaskItem::getItemKey, itemKey.trim()));
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批处理明细不存在");
        }
        return item;
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
        List<BatchTaskItemSubmitRequest> items = request.getItems() == null ? List.of() : request.getItems();
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

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
