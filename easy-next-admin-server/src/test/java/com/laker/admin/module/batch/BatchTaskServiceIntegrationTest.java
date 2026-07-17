package com.laker.admin.module.batch;

import com.laker.admin.module.batch.dto.BatchTaskItemSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskItemView;
import com.laker.admin.module.batch.dto.BatchTaskSubmitRequest;
import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import com.laker.admin.module.batch.runner.EasyBatchItemClaim;
import com.laker.admin.module.batch.service.BatchTaskService;
import com.laker.admin.module.batch.service.IBatchTaskItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(task.getParamsJson()).isEqualTo("{\"source\":\"upload\"}");
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
    void submitTaskRejectsInvalidExecutionMetadata() {
        BatchTaskSubmitRequest invalidRun = submitRequest();
        invalidRun.setRunNo(0);
        assertThatThrownBy(() -> batchTaskService.submitTask(invalidRun))
                .hasMessageContaining("执行序号必须大于 0");

        BatchTaskSubmitRequest invalidWorkType = submitRequest();
        invalidWorkType.getItems().get(0).setWorkType("UNKNOWN");
        assertThatThrownBy(() -> batchTaskService.submitTask(invalidWorkType))
                .hasMessageContaining("工作单元类型");
    }

    @Test
    void appendItemsAddsOnlyNewItemKeysAndRefreshesTotalCount() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());

        BatchTaskView appended = batchTaskService.appendItems(task.getId(), List.of(
                item("u002", "李四重复", "{\"phone\":\"bad-phone\"}"),
                item("u003", "王五", "{\"phone\":\"13800000003\"}")
        ));

        assertThat(appended.getTotalCount()).isEqualTo(3);
        List<BatchTaskItem> items = itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, task.getId())
                .orderByAsc(BatchTaskItem::getItemKey)
                .list();
        assertThat(items).hasSize(3);
        assertThat(items).extracting(BatchTaskItem::getItemKey).containsExactly("u001", "u002", "u003");
    }

    @Test
    void itemTransitionsUpdateProgressAndCompleteAsPartialSuccess() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());

        assertThat(markSuccess(task.getId(), "u001", "已导入")).isTrue();
        assertThat(markFailed(task.getId(), "u002", "手机号格式错误")).isTrue();
        BatchTaskView completed = batchTaskService.completeTask(task.getId());

        BatchTaskItem success = item(task.getId(), "u001");
        BatchTaskItem failed = item(task.getId(), "u002");

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
    void activeLeasePreventsAnotherWorkerFromClaimingTheSameItem() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());
        EasyBatchItemClaim running = claim(task.getId(), "u001", "worker-running");
        assertThat(markSuccess(task.getId(), "u002", "已导入")).isTrue();

        BatchTaskItem runningItem = itemService.getById(running.itemId());
        assertThat(runningItem.getStatus()).isEqualTo("RUNNING");
        assertThat(runningItem.getStartedAt()).isNotNull();
        assertThat(runningItem.getFinishedAt()).isNull();
        assertThat(batchTaskService.claimItem(task.getId(), "u001", "worker-other", Duration.ofMinutes(5))).isEmpty();
    }

    @Test
    void claimAllowsOnlyOneWorkerToOwnAnItemBeforeLeaseExpires() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());

        EasyBatchItemClaim claim = batchTaskService.claimItem(task.getId(), "u001", "worker-a", Duration.ofMinutes(5))
                .orElseThrow();

        assertThat(batchTaskService.claimItem(task.getId(), "u001", "worker-b", Duration.ofMinutes(5))).isEmpty();
        BatchTaskItem item = itemService.getById(claim.itemId());
        assertThat(item.getStatus()).isEqualTo("RUNNING");
        assertThat(item.getWorkerId()).isEqualTo("worker-a");
        assertThat(item.getLeaseToken()).isEqualTo(claim.leaseToken());
        assertThat(item.getLeaseUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void expiredLeaseCanBeReclaimedAndRejectsStaleWorkerResult() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());
        EasyBatchItemClaim staleClaim = batchTaskService
                .claimItem(task.getId(), "u001", "worker-a", Duration.ofMinutes(5))
                .orElseThrow();
        BatchTaskItem expiredItem = itemService.getById(staleClaim.itemId());
        expiredItem.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        itemService.updateById(expiredItem);

        EasyBatchItemClaim activeClaim = batchTaskService
                .claimItem(task.getId(), "u001", "worker-b", Duration.ofMinutes(5))
                .orElseThrow();

        assertThat(activeClaim.leaseToken()).isNotEqualTo(staleClaim.leaseToken());
        assertThat(batchTaskService.markClaimSuccess(staleClaim, "旧实例完成")).isFalse();
        assertThat(batchTaskService.markClaimSuccess(activeClaim, "接管完成")).isTrue();
        BatchTaskItem completed = itemService.getById(activeClaim.itemId());
        assertThat(completed.getStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getWorkerId()).isEqualTo("worker-b");
        assertThat(completed.getResultMessage()).isEqualTo("接管完成");
    }

    @Test
    void onlyCurrentOwnerCanRenewLeaseAndWriteFailure() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());
        EasyBatchItemClaim claim = batchTaskService
                .claimItem(task.getId(), "u001", "worker-a", Duration.ofSeconds(30))
                .orElseThrow();
        EasyBatchItemClaim wrongToken = new EasyBatchItemClaim(claim.itemId(), claim.taskId(), claim.itemKey(),
                claim.workerId(), "wrong-token", claim.leaseUntil());

        assertThat(batchTaskService.renewClaim(wrongToken, Duration.ofMinutes(5))).isFalse();
        assertThat(batchTaskService.renewClaim(claim, Duration.ofMinutes(5))).isTrue();
        assertThat(itemService.getById(claim.itemId()).getLeaseUntil()).isAfter(claim.leaseUntil());
        assertThat(batchTaskService.markClaimFailed(wrongToken, "旧实例失败")).isFalse();
        assertThat(batchTaskService.markClaimFailed(claim, "远程调用失败")).isTrue();

        BatchTaskItem failed = itemService.getById(claim.itemId());
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorMessage()).isEqualTo("远程调用失败");
        assertThat(failed.getLeaseToken()).isNull();
        assertThat(failed.getLeaseUntil()).isNull();
    }

    @Test
    void completeTaskWaitsWhileClaimedItemsAreStillRunning() {
        BatchTaskView task = batchTaskService.submitTask(submitRequest());
        batchTaskService.startTask(task.getId());
        batchTaskService.claimItem(task.getId(), "u001", "worker-a", Duration.ofMinutes(5)).orElseThrow();

        BatchTaskView running = batchTaskService.completeTask(task.getId());

        assertThat(running.getStatus()).isEqualTo("RUNNING");
        assertThat(running.getFinishedAt()).isNull();
        assertThat(running.getProgressPercent()).isZero();
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
        markSuccess(task.getId(), "u001", "已导入");
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
    void rerunCreatesNewExecutionAndKeepsTerminalHistoryImmutable() {
        BatchTaskSubmitRequest request = submitRequest();
        BatchTaskView firstRun = batchTaskService.submitTask(request);
        batchTaskService.startTask(firstRun.getId());
        markFailed(firstRun.getId(), "u001", "首次执行失败");
        markSuccess(firstRun.getId(), "u002", "首次执行成功");
        BatchTaskView completed = batchTaskService.completeTask(firstRun.getId());

        request.setRunNo(2);
        request.setItems(List.of(item("u001", "张三", "{\"phone\":\"13800000001\"}")));
        BatchTaskView secondRun = batchTaskService.submitTask(request);

        assertThat(secondRun.getId()).isNotEqualTo(firstRun.getId());
        assertThat(secondRun.getBusinessKey()).isEqualTo(firstRun.getBusinessKey());
        assertThat(secondRun.getRunNo()).isEqualTo(2);
        assertThat(secondRun.getStatus()).isEqualTo("PENDING");
        assertThat(secondRun.getTotalCount()).isEqualTo(1);
        assertThat(batchTaskService.getTask(firstRun.getId()))
                .extracting(BatchTaskView::getStatus, BatchTaskView::getRunNo)
                .containsExactly(completed.getStatus(), 1);
    }

    private BatchTaskSubmitRequest submitRequest() {
        BatchTaskSubmitRequest request = new BatchTaskSubmitRequest();
        request.setTaskType("USER_IMPORT");
        request.setTaskName("用户导入");
        request.setBusinessKey("import-20260625-001");
        request.setTriggerType("API");
        request.setTriggerRefId("upload-001");
        request.setParamsJson("{\"source\":\"upload\"}");
        request.setItems(List.of(
                item("u001", "张三", "{\"phone\":\"13800000001\"}"),
                item("u002", "李四", "{\"phone\":\"bad-phone\"}")
        ));
        return request;
    }

    private BatchTaskItemSubmitRequest item(String key, String name, String inputJson) {
        BatchTaskItemSubmitRequest item = new BatchTaskItemSubmitRequest();
        item.setItemKey(key);
        item.setItemName(name);
        item.setInputJson(inputJson);
        return item;
    }

    private EasyBatchItemClaim claim(Long taskId, String itemKey, String workerId) {
        return batchTaskService.claimItem(taskId, itemKey, workerId, Duration.ofMinutes(5)).orElseThrow();
    }

    private boolean markSuccess(Long taskId, String itemKey, String message) {
        return batchTaskService.markClaimSuccess(claim(taskId, itemKey, "test-worker"), message);
    }

    private boolean markFailed(Long taskId, String itemKey, String message) {
        return batchTaskService.markClaimFailed(claim(taskId, itemKey, "test-worker"), message);
    }

    private BatchTaskItem item(Long taskId, String itemKey) {
        return itemService.lambdaQuery()
                .eq(BatchTaskItem::getTaskId, taskId)
                .eq(BatchTaskItem::getItemKey, itemKey)
                .one();
    }
}
