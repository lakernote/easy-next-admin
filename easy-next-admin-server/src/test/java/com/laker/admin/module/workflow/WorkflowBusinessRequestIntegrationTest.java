package com.laker.admin.module.workflow;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.business.number.service.BusinessNumberService;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.service.ISysFileService;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.purchase.dto.PurchaseApplyRequest;
import com.laker.admin.module.workflow.purchase.dto.PurchaseRequestView;
import com.laker.admin.module.workflow.purchase.service.IPurchaseRequestService;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentView;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.repair.dto.RepairApplyRequest;
import com.laker.admin.module.workflow.repair.dto.RepairRequestView;
import com.laker.admin.module.workflow.repair.service.IRepairRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class WorkflowBusinessRequestIntegrationTest {
    private static final long ADMIN = 202604280101000001L;
    private static final long ROOT_DEPT = 202604280103000001L;
    private static final long OPS = 202604280101000024L;

    @Autowired
    private IPurchaseRequestService purchaseRequestService;
    @Autowired
    private IRepairRequestService repairRequestService;
    @Autowired
    private ISysFileService sysFileService;
    @Autowired
    private BusinessNumberService businessNumberService;
    @Autowired
    private IWfTaskService taskService;
    @Autowired
    private IWfHistoricCcService historicCcService;
    @Autowired
    private IWfWorkflowRuntimeService workflowRuntimeService;

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void purchaseApplyShouldCreateBusinessRecordAndWorkflowInstance() {
        asUser(ADMIN);
        PurchaseApplyRequest request = new PurchaseApplyRequest();
        request.setItemName("办公显示器");
        request.setCategory("IT_EQUIPMENT");
        request.setQuantity(2);
        request.setEstimatedAmount(new BigDecimal("3200.00"));
        request.setRequiredDate(LocalDate.now().plusDays(7));
        request.setReason("客服坐席补充双屏办公设备");

        PurchaseRequestView applied = purchaseRequestService.apply(request);

        assertThat(applied.getRequestNo()).startsWith("PR-");
        assertThat(applied.getStatus()).isEqualTo("APPROVING");
        assertThat(applied.getWorkflowInstanceId()).isNotNull();
        assertThat(onlyPendingTask(applied.getWorkflowInstanceId()).getNodeName()).isEqualTo("负责人审批");

        workflowRuntimeService.approve(onlyPendingTask(applied.getWorkflowInstanceId()).getId(), action("同意采购"));

        assertThat(purchaseRequestService.detail(applied.getId()).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void requestNoShouldUseIndependentDailyDatabaseSequences() {
        String firstPurchaseNo = businessNumberService.nextNumber("PURCHASE_REQUEST");
        String secondPurchaseNo = businessNumberService.nextNumber("PURCHASE_REQUEST");
        String firstRepairNo = businessNumberService.nextNumber("REPAIR_REQUEST");
        String secondRepairNo = businessNumberService.nextNumber("REPAIR_REQUEST");

        assertThat(firstPurchaseNo).matches("PR-\\d{8}-\\d{6}");
        assertThat(secondPurchaseNo).matches("PR-\\d{8}-\\d{6}");
        assertThat(firstRepairNo).matches("RP-\\d{8}-\\d{6}");
        assertThat(secondRepairNo).matches("RP-\\d{8}-\\d{6}");
        assertThat(sequenceValue(secondPurchaseNo)).isEqualTo(sequenceValue(firstPurchaseNo) + 1);
        assertThat(sequenceValue(secondRepairNo)).isEqualTo(sequenceValue(firstRepairNo) + 1);
    }

    @Test
    void repairApplyShouldRouteToOpsAndArchiveAfterApproval() {
        asUser(ADMIN);
        RepairApplyRequest request = new RepairApplyRequest();
        request.setRepairType("DEVICE");
        request.setAssetName("会议室投影仪");
        request.setUrgency("HIGH");
        request.setFaultTime(LocalDateTime.now().minusHours(2));
        request.setLocation("深圳总部 12F 会议室");
        request.setDescription("投影画面频繁闪烁，影响客户会议");
        RepairAttachmentView attachment = repairImage(202605080001L, "projector-fault.jpg");
        request.setAttachments(List.of(attachment));

        RepairRequestView applied = repairRequestService.apply(request);

        assertThat(applied.getRequestNo()).startsWith("RP-");
        assertThat(applied.getStatus()).isEqualTo("APPROVING");
        assertThat(applied.getAttachments()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getFileId()).isEqualTo(attachment.getFileId());
                    assertThat(item.getFileName()).isEqualTo("projector-fault.jpg");
                    assertThat(item.getUrl()).contains("/api/workflow/repair/requests/attachments/");
                });
        assertThat(sysFileService.getById(attachment.getFileId()))
                .satisfies(file -> {
                    assertThat(file.getBusinessType()).isEqualTo("repair");
                    assertThat(file.getBusinessId()).isEqualTo(applied.getId());
                });
        WfTask opsTask = onlyPendingTask(applied.getWorkflowInstanceId());
        assertThat(opsTask.getAssigneeId()).isEqualTo(OPS);
        assertThat(workflowRuntimeService.detail(applied.getWorkflowInstanceId()).getInstance().getVariablesJson())
                .contains("\"repairAttachments\"")
                .contains(String.valueOf(attachment.getFileId()));

        asUser(OPS);
        assertThat(repairRequestService.detailByWorkflowInstance(applied.getWorkflowInstanceId()).getAttachments())
                .singleElement()
                .extracting(RepairAttachmentView::getFileId)
                .isEqualTo(attachment.getFileId());
        workflowRuntimeService.approve(opsTask.getId(), action("已受理并安排维修"));

        asUser(ADMIN);
        assertThat(repairRequestService.detail(applied.getId()).getStatus()).isEqualTo("APPROVED");
        assertThat(repairRequestService.detail(applied.getId()).getAttachments()).hasSize(1);
        assertThat(historicCcService.lambdaQuery()
                .eq(WfHistoricCc::getInstanceId, applied.getWorkflowInstanceId())
                .one())
                .satisfies(cc -> {
                    assertThat(cc.getNodeKey()).isEqualTo("cc_audit");
                    assertThat(cc.getNodeName()).isEqualTo("审计备案");
                });
    }

    private WfTask onlyPendingTask(Long instanceId) {
        return taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getStatus, "PENDING")
                .one();
    }

    private WfTaskActionRequest action(String comment) {
        WfTaskActionRequest request = new WfTaskActionRequest();
        request.setComment(comment);
        return request;
    }

    private void asUser(long userId) {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(userId)
                .userName("u" + userId)
                .nickName(userId == OPS ? "周运维" : "超级管理员")
                .deptId(ROOT_DEPT)
                .superAdmin(userId == ADMIN)
                .build());
    }

    private RepairAttachmentView repairImage(long fileId, String fileName) {
        SysFile file = new SysFile();
        file.setFileId(fileId);
        file.setFilePath("repair/" + fileName);
        file.setFileName(fileName);
        file.setOriginalName(fileName);
        file.setStorageName(fileName);
        file.setStorageType("LOCAL");
        file.setFileSize(256_000L);
        file.setContentType("image/jpeg");
        file.setUserId(ADMIN);
        file.setNickName("超级管理员");
        file.setCreateTime(LocalDateTime.now());
        sysFileService.save(file);

        RepairAttachmentView attachment = new RepairAttachmentView();
        attachment.setFileId(fileId);
        attachment.setFileName(fileName);
        attachment.setContentType("image/jpeg");
        attachment.setFileSize(256_000L);
        attachment.setUrl("/api/workflow/repair/requests/attachments/" + fileId);
        return attachment;
    }

    private long sequenceValue(String requestNo) {
        return Long.parseLong(requestNo.substring(requestNo.lastIndexOf('-') + 1));
    }
}
