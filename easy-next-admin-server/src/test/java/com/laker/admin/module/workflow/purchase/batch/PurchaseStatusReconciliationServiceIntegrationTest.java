package com.laker.admin.module.workflow.purchase.batch;

import com.laker.admin.module.batch.dto.BatchTaskView;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import com.laker.admin.module.batch.enums.BatchTriggerType;
import com.laker.admin.module.batch.service.IBatchTaskItemService;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.mapper.WfHistoricProcessInstanceMapper;
import com.laker.admin.module.workflow.purchase.entity.BizPurchaseRequest;
import com.laker.admin.module.workflow.purchase.mapper.BizPurchaseRequestMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PurchaseStatusReconciliationServiceIntegrationTest {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 28);

    @Autowired
    private PurchaseStatusReconciliationService reconciliationService;
    @Autowired
    private BizPurchaseRequestMapper purchaseMapper;
    @Autowired
    private WfHistoricProcessInstanceMapper processInstanceMapper;
    @Autowired
    private IBatchTaskItemService itemService;

    @Test
    void reconcilesStatusDriftThroughPersistedPartitions() throws Exception {
        long purchaseId = 202606280001L;
        long instanceId = 202606280101L;
        try {
            processInstanceMapper.insert(processInstance(instanceId, "APPROVED"));
            purchaseMapper.insert(purchase(purchaseId, instanceId, "APPROVING"));

            BatchTaskView task = reconciliationService.run(
                    BUSINESS_DATE, 1, BatchTriggerType.MANUAL.code(), "integration-test");

            assertThat(task.getStatus()).isEqualTo("SUCCESS");
            assertThat(task.getTaskType()).isEqualTo(PurchaseStatusReconciliationService.TASK_TYPE);
            assertThat(task.getBusinessKey()).endsWith(":DAY:20260628");
            assertThat(task.getRunNo()).isEqualTo(1);
            assertThat(task.getTotalCount()).isEqualTo(1);
            assertThat(purchaseMapper.selectById(purchaseId).getStatus()).isEqualTo("APPROVED");

            List<BatchTaskItem> items = itemService.lambdaQuery()
                    .eq(BatchTaskItem::getTaskId, task.getId())
                    .list();
            assertThat(items).hasSize(1);
            assertThat(items).extracting(BatchTaskItem::getWorkType).containsOnly("PARTITION");
            assertThat(items).extracting(BatchTaskItem::getAttemptCount).containsOnly(1);
            assertThat(items).allSatisfy(item -> assertThat(item.getInputJson()).contains("2026-06-28"));
        } finally {
            purchaseMapper.deleteById(purchaseId);
            processInstanceMapper.deleteById(instanceId);
        }
    }

    private WfHistoricProcessInstance processInstance(long id, String status) {
        LocalDateTime updatedAt = BUSINESS_DATE.atTime(12, 0);
        WfHistoricProcessInstance instance = new WfHistoricProcessInstance();
        instance.setId(id);
        instance.setProcessKey("purchase_approval");
        instance.setBusinessType("purchase");
        instance.setBusinessId("PO-TEST-001");
        instance.setStatus(status);
        instance.setStartedAt(updatedAt.minusHours(1));
        instance.setCreatedAt(updatedAt.minusHours(1));
        instance.setUpdatedAt(updatedAt);
        return instance;
    }

    private BizPurchaseRequest purchase(long id, long instanceId, String status) {
        LocalDateTime updatedAt = BUSINESS_DATE.atTime(11, 0);
        BizPurchaseRequest purchase = new BizPurchaseRequest();
        purchase.setId(id);
        purchase.setRequestNo("PO-TEST-001");
        purchase.setApplicantId(1L);
        purchase.setItemName("测试服务器");
        purchase.setCategory("IT_EQUIPMENT");
        purchase.setQuantity(1);
        purchase.setEstimatedAmount(new BigDecimal("10000.00"));
        purchase.setRequiredDate(BUSINESS_DATE.plusDays(7));
        purchase.setReason("验证采购状态对账");
        purchase.setStatus(status);
        purchase.setWorkflowInstanceId(instanceId);
        purchase.setCreatedAt(updatedAt.minusDays(1));
        purchase.setUpdatedAt(updatedAt);
        return purchase;
    }
}
