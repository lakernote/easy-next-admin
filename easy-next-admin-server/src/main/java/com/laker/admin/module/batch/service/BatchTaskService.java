package com.laker.admin.module.batch.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.batch.dto.BatchTaskItemQuery;
import com.laker.admin.module.batch.dto.BatchTaskItemView;
import com.laker.admin.module.batch.dto.BatchTaskQuery;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTask;

public interface BatchTaskService extends IService<BatchTask> {
    BatchTaskView submitTask(BatchTaskSubmitRequest request);

    BatchTaskView startTask(Long taskId);

    BatchTaskItemView markItemSuccess(Long taskId, String itemKey, String resultMessage);

    BatchTaskItemView markItemFailed(Long taskId, String itemKey, String errorMessage);

    BatchTaskView completeTask(Long taskId);

    BatchTaskView requestCancel(Long taskId, String reason);

    boolean shouldStop(Long taskId);

    BatchTaskView retryFailedItems(Long taskId);

    BatchTaskView getTask(Long taskId);

    PageResponse<BatchTaskView> pageTasks(BatchTaskQuery query);

    PageResponse<BatchTaskItemView> pageItems(Long taskId, BatchTaskItemQuery query);
}
