package com.laker.admin.module.batch;

import com.laker.admin.module.batch.dto.BatchTaskItemSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskItemView;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import com.laker.admin.module.batch.service.BatchTaskService;
import com.laker.admin.module.batch.service.IBatchTaskItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class BatchTaskServiceIntegrationTest {

    @Autowired
    private BatchTaskService batchTaskService;

    @Autowired
    private IBatchTaskItemService itemService;

    @Test
    void submitTaskCreatesItemsAndKeepsProgressAtZero() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());

        assertThat(task.getTaskType()).isEqualTo("USER_IMPORT");
        assertThat(task.getTriggerType()).isEqualTo("API");
        assertThat(task.getBusinessKey()).isEqualTo("import-20260625-001");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getTotalCount()).isEqualTo(2);
        assertThat(task.getSuccessCount()).isZero();
        assertThat(task.getFailedCount()).isZero();
        assertThat(task.getProgressPercent()).isZero();

        List<BatchTaskItem> items = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .orderByAsc(BatchTaskItem::getItemKey)
                .list();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(BatchTaskItem::getItemKey).containsExactly("u001", "u002");
        assertThat(items).extracting(BatchTaskItem::getStatus).containsOnly("PENDING");
    }

    @Test
    void itemTransitionsUpdateProgressAndCompleteAsPartialSuccess() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());

        BatchTaskItemView success = batchTaskService.markItemSuccess(task.getId(), "u001", "已导入");
        BatchTaskItemView failed = batchTaskService.markItemFailed(task.getId(), "u002", "手机号格式错误");
        BatchTaskView completed = batchTaskService.completeTask(task.getId());

        assertThat(success.getStatus()).isEqualTo("SUCCESS");
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorMessage()).isEqualTo("手机号格式错误");
        assertThat(completed.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(completed.getSuccessCount()).isEqualTo(1);
        assertThat(completed.getFailedCount()).isEqualTo(1);
        assertThat(completed.getProgressPercent()).isEqualTo(100);
    }

    @Test
    void requestCancelMarksRunningTaskAndSignalsWorkersToStop() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());

        BatchTaskView canceling = batchTaskService.requestCancel(task.getId(), "人工取消");

        assertThat(canceling.getStatus()).isEqualTo("CANCELING");
        assertThat(canceling.getCancelRequested()).isTrue();
        assertThat(canceling.getErrorMessage()).isEqualTo("人工取消");
        assertThat(batchTaskService.shouldStop(task.getId())).isTrue();
    }

    @Test
    void requestCancelClosesPendingTaskAndSkipsUnprocessedItems() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());

        BatchTaskView canceled = batchTaskService.requestCancel(task.getId(), "人工取消");

        assertThat(canceled.getStatus()).isEqualTo("CANCELED");
        assertThat(canceled.getCancelRequested()).isTrue();
        assertThat(canceled.getSkippedCount()).isEqualTo(2);
        assertThat(canceled.getProgressPercent()).isEqualTo(100);
        assertThat(canceled.getFinishedAt()).isNotNull();

        List<BatchTaskItem> items = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .orderByAsc(BatchTaskItem::getItemKey)
                .list();
        assertThat(items).extracting(BatchTaskItem::getStatus).containsOnly("SKIPPED");
    }

    @Test
    void completeTaskAfterCancelClosesTaskAndSkipsRemainingItems() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());
        batchTaskService.markItemSuccess(task.getId(), "u001", "已导入");
        batchTaskService.requestCancel(task.getId(), "人工取消");

        BatchTaskView canceled = batchTaskService.completeTask(task.getId());

        assertThat(canceled.getStatus()).isEqualTo("CANCELED");
        assertThat(canceled.getSuccessCount()).isEqualTo(1);
        assertThat(canceled.getSkippedCount()).isEqualTo(1);
        assertThat(canceled.getProgressPercent()).isEqualTo(100);
        assertThat(canceled.getFinishedAt()).isNotNull();

        BatchTaskItem skippedItem = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .eq(BatchTaskItem::getItemKey, "u002")
                .one();
        assertThat(skippedItem.getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    void retryFailedItemsResetsOnlyFailuresAndPreservesCompletedItems() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());
        batchTaskService.markItemSuccess(task.getId(), "u001", "已导入");
        batchTaskService.markItemFailed(task.getId(), "u002", "手机号格式错误");
        batchTaskService.completeTask(task.getId());

        BatchTaskView retried = batchTaskService.retryFailedItems(task.getId());

        assertThat(retried.getStatus()).isEqualTo("PENDING");
        assertThat(retried.getSuccessCount()).isEqualTo(1);
        assertThat(retried.getFailedCount()).isZero();
        assertThat(retried.getProgressPercent()).isEqualTo(50);

        BatchTaskItem retryItem = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .eq(BatchTaskItem::getItemKey, "u002")
                .one();
        assertThat(retryItem.getStatus()).isEqualTo("PENDING");
        assertThat(retryItem.getRetryCount()).isEqualTo(1);
        assertThat(retryItem.getErrorMessage()).isNull();
    }

    private BatchTaskSubmitRequest submitRequest() {
        BatchTaskSubmitRequest request = new BatchTaskSubmitRequest();
        request.setTaskType("USER_IMPORT");
        request.setTaskName("用户导入");
        request.setBusinessKey("import-20260625-001");
        request.setTriggerType("API");
        request.setTriggerRefId("upload-001");
        request.setItems(List.of(
                item("u001", "张三", "{\"phone\":\"13800000001\"}"),
                item("u002", "李四", "{\"phone\":\"bad-phone\"}")
        ));
        return request;
    }

    private BatchTaskItemSubmitRequest item(String key, String name, String payload) {
        BatchTaskItemSubmitRequest item = new BatchTaskItemSubmitRequest();
        item.setItemKey(key);
        item.setItemName(name);
        item.setPayload(payload);
        return item;
    }
}
