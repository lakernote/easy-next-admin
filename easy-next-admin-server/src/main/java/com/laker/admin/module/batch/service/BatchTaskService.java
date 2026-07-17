package com.laker.admin.module.batch.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.batch.dto.BatchTaskItemQuery;
import com.laker.admin.module.batch.dto.BatchTaskItemSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskItemView;
import com.laker.admin.module.batch.dto.BatchTaskQuery;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTask;
import com.laker.admin.module.batch.runner.EasyBatchItemClaim;
import com.laker.admin.module.batch.runner.EasyBatchClaimedItem;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface BatchTaskService extends IService<BatchTask> {
    BatchTaskView submitTask(BatchTaskSubmitRequest request);

    BatchTaskView appendItems(Long taskId, List<BatchTaskItemSubmitRequest> items);

    BatchTaskView startTask(Long taskId);

    Optional<EasyBatchItemClaim> claimItem(Long taskId, String itemKey, String workerId, Duration leaseDuration);

    Optional<EasyBatchClaimedItem> claimNextItem(Long taskId, String workerId, Duration leaseDuration);

    boolean renewClaim(EasyBatchItemClaim claim, Duration leaseDuration);

    boolean markClaimSuccess(EasyBatchItemClaim claim, String resultMessage);

    boolean markClaimFailed(EasyBatchItemClaim claim, String errorMessage);

    BatchTaskView completeTask(Long taskId);

    BatchTaskView requestCancel(Long taskId, String reason);

    boolean shouldStop(Long taskId);

    BatchTaskView skipRunnableItems(Long taskId, String reason);

    BatchTaskView getTask(Long taskId);

    PageResponse<BatchTaskView> pageTasks(BatchTaskQuery query);

    PageResponse<BatchTaskItemView> pageItems(Long taskId, BatchTaskItemQuery query);
}
