package com.laker.admin.module.workflow;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.message.entity.UserMessage;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysDeptService;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.dto.WfInstanceActionRequest;
import com.laker.admin.module.workflow.dto.WfParticipantView;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.entity.WfEvent;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfEventService;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class WorkflowRuntimeIntegrationTest {
    private static final long INITIATOR = 7001L;
    private static final long APPROVER_A = 7002L;
    private static final long APPROVER_B = 7003L;
    private static final long INITIATOR_DEPT = 202604280103000103L;
    private static final long APPROVER_A_DEPT = 202604280103000104L;
    private static final long APPROVER_B_DEPT = 202604280103000105L;

    @Autowired
    private IWfWorkflowRuntimeService runtimeService;
    @Autowired
    private IWfProcessDefinitionService definitionService;
    @Autowired
    private IWfProcessDefinitionVersionService versionService;
    @Autowired
    private IWfProcessInstanceService instanceService;
    @Autowired
    private IWfTaskService taskService;
    @Autowired
    private IWfHistoricTaskService historicTaskService;
    @Autowired
    private IWfHistoricProcessInstanceService historicInstanceService;
    @Autowired
    private IWfEventService eventService;
    @Autowired
    private ISysUserService sysUserService;
    @Autowired
    private ISysDeptService sysDeptService;
    @Autowired
    private UserMessageService userMessageService;

    @BeforeEach
    void seedWorkflowUsers() {
        saveWorkflowUser(INITIATOR, "workflow_initiator", "流程发起人", INITIATOR_DEPT);
        saveWorkflowUser(APPROVER_A, "workflow_approver_a", "审批人甲", APPROVER_A_DEPT);
        saveWorkflowUser(APPROVER_B, "workflow_approver_b", "审批人乙", APPROVER_B_DEPT);
        saveWorkflowDepartment(INITIATOR_DEPT, "客户成功中心", 202604280103000001L, APPROVER_A);
        saveWorkflowDepartment(APPROVER_A_DEPT, "运营交付中心", 202604280103000001L, APPROVER_A);
        saveWorkflowDepartment(APPROVER_B_DEPT, "财务行政部", 202604280103000001L, APPROVER_B);
    }

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldCompleteSequentialApprovalInDeclaredOrder() {
        createDefinition("it_seq", graph(nodes(
                node("start", "circle", "开始", "START"),
                node("submit", "rect", "提交申请", "SUBMIT"),
                approvalNode("approve", "顺序审批", "SEQUENTIAL", APPROVER_A, APPROVER_B),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "submit"), edge("submit", "approve"), edge("approve", "end"))));

        WfProcessInstanceDetail started = start("it_seq", "顺序审批端到端");
        List<WfTask> firstPendingTasks = pendingTasks(started.getInstance().getId());
        assertThat(firstPendingTasks).extracting(WfTask::getAssigneeId).containsExactly(APPROVER_A);

        asUser(APPROVER_A);
        runtimeService.approve(firstPendingTasks.get(0).getId(), action("一审通过"));
        List<WfTask> secondPendingTasks = pendingTasks(started.getInstance().getId());
        assertThat(secondPendingTasks).extracting(WfTask::getAssigneeId).containsExactly(APPROVER_B);
        assertThat(instanceStatus(started)).isEqualTo("RUNNING");

        asUser(APPROVER_B);
        runtimeService.approve(secondPendingTasks.get(0).getId(), action("二审通过"));

        assertThat(instanceStatus(started)).isEqualTo("APPROVED");
        assertThat(taskStatuses(started.getInstance().getId())).containsExactly("APPROVED", "APPROVED");
    }

    @Test
    void shouldArchiveFinishedInstanceAndTasksOutsideRuntimeTables() {
        createDefinition("it_archive", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_archive", "归档端到端");
        WfTask pendingTask = onlyPendingTask(started.getInstance().getId());

        asUser(APPROVER_A);
        runtimeService.approve(pendingTask.getId(), action("同意归档"));

        assertThat(definitionService.getById(started.getInstance().getDefinitionId())).isNotNull();
        assertThat(EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, started.getInstance().getId())
                .count())).isZero();
        assertThat(instanceService.getById(started.getInstance().getId())).isNull();
        assertThat(historicInstanceService.getById(started.getInstance().getId()).getStatus()).isEqualTo("APPROVED");
        assertThat(historicTaskService.lambdaQuery()
                .eq(WfHistoricTask::getInstanceId, started.getInstance().getId())
                .list())
                .extracting(WfHistoricTask::getStatus)
                .containsExactly("APPROVED");
        assertThat(runtimeService.detail(started.getInstance().getId()).getInstance().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void detailShouldReturnParticipantsOutsideCurrentUserDataScope() {
        createDefinition("it_participant_scope", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_participant_scope", "参与人详情");
        asDeptScopedUser(INITIATOR, INITIATOR_DEPT);

        assertThat(sysUserService.listWorkflowAssignees())
                .extracting(item -> item.getValue())
                .doesNotContain(String.valueOf(APPROVER_A));

        WfProcessInstanceDetail detail = runtimeService.detail(started.getInstance().getId());

        assertThat(detail.getParticipants())
                .extracting(WfParticipantView::getValue)
                .contains(String.valueOf(INITIATOR), String.valueOf(APPROVER_A));
        assertThat(detail.getParticipants())
                .extracting(WfParticipantView::getName)
                .contains("审批人甲（workflow_approver_a）");
    }

    @Test
    void shouldResolveManagerApproverFromUserRelation() {
        SysUser initiator = sysUserService.getById(INITIATOR);
        initiator.setManagerUserId(APPROVER_A);
        sysUserService.updateById(initiator);
        createDefinition("it_manager_rule", graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNodeByRule("approve", "直属上级审批", "ANY_ONE", "MANAGER"),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "approve"), edge("approve", "end"))));

        WfProcessInstanceDetail started = start("it_manager_rule", "直属上级审批端到端");
        WfTask pendingTask = onlyPendingTask(started.getInstance().getId());

        assertThat(pendingTask.getAssigneeId()).isEqualTo(APPROVER_A);
        assertThat(pendingTask.getAssignmentRuleName()).isEqualTo("发起人直属上级");
        assertThat(pendingTask.getAssignmentResolvePath()).contains("流程发起人", "直属上级", "审批人甲");
    }

    @Test
    void shouldResolveDepartmentLeaderAndRecordAssignmentPath() {
        createDefinition("it_dept_leader_rule", graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNodeByRule("approve", "部门负责人审批", "ANY_ONE", "DEPT_LEADER"),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "approve"), edge("approve", "end"))));

        WfProcessInstanceDetail started = start("it_dept_leader_rule", "部门负责人审批端到端");
        WfTask pendingTask = onlyPendingTask(started.getInstance().getId());

        assertThat(pendingTask.getAssigneeId()).isEqualTo(APPROVER_A);
        assertThat(pendingTask.getAssignmentRuleName()).isEqualTo("发起人部门负责人");
        assertThat(pendingTask.getAssignmentResolvePath()).contains("客户成功中心", "审批人甲");
    }

    @Test
    void shouldRejectStartWhenFixedAssigneeIsDisabled() {
        SysUser approver = sysUserService.getById(APPROVER_A);
        approver.setEnable(0);
        sysUserService.updateById(approver);
        createDefinition("it_disabled_fixed_assignee", simpleApprovalGraph("approve", APPROVER_A));

        assertThatThrownBy(() -> start("it_disabled_fixed_assignee", "停用固定审批人"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已停用");
    }

    @Test
    void shouldRejectStartWhenSelectedAssigneeIsDisabled() {
        SysUser approver = sysUserService.getById(APPROVER_A);
        approver.setEnable(0);
        sysUserService.updateById(approver);
        createDefinition("it_disabled_selected_assignee", graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNodeByRule("approve", "发起人选择审批", "ANY_ONE", "INITIATOR_SELECTED"),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "approve"), edge("approve", "end"))));

        assertThatThrownBy(() -> start("it_disabled_selected_assignee", "停用自选审批人", APPROVER_A))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已停用");
    }

    @Test
    void shouldJumpToUpperDepartmentLeaderWhenInitiatorIsDepartmentLeader() {
        SysDept dept = sysDeptService.getById(INITIATOR_DEPT);
        dept.setLeaderUserId(INITIATOR);
        sysDeptService.updateById(dept);
        createDefinition("it_dept_leader_self_jump", graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNodeByRule("approve", "部门负责人审批", "ANY_ONE", "DEPT_LEADER"),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "approve"), edge("approve", "end"))));

        WfProcessInstanceDetail started = start("it_dept_leader_self_jump", "部门负责人自审上跳");
        WfTask pendingTask = onlyPendingTask(started.getInstance().getId());

        assertThat(pendingTask.getAssigneeId()).isEqualTo(202604280101000001L);
        assertThat(pendingTask.getAssignmentRuleName()).contains("自审上跳");
        assertThat(pendingTask.getAssignmentResolvePath()).contains("自动上跳", "超级管理员");
    }

    @Test
    void shouldWaitForEveryApproverInAllApprovalNode() {
        createDefinition("it_all", graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNode("approve", "会签审批", "ALL", APPROVER_A, APPROVER_B),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "approve"), edge("approve", "end"))));

        WfProcessInstanceDetail started = start("it_all", "会签审批端到端");
        List<WfTask> pendingTasks = pendingTasks(started.getInstance().getId());
        assertThat(pendingTasks).extracting(WfTask::getAssigneeId).containsExactly(APPROVER_A, APPROVER_B);

        asUser(APPROVER_A);
        runtimeService.approve(taskOf(pendingTasks, APPROVER_A).getId(), action("甲同意"));
        assertThat(instanceStatus(started)).isEqualTo("RUNNING");
        assertThat(pendingTasks(started.getInstance().getId())).extracting(WfTask::getAssigneeId).containsExactly(APPROVER_B);

        asUser(APPROVER_B);
        runtimeService.approve(taskOf(pendingTasks(started.getInstance().getId()), APPROVER_B).getId(), action("乙同意"));

        assertThat(instanceStatus(started)).isEqualTo("APPROVED");
    }

    @Test
    void shouldHoldApprovalNodeUntilAddSignTaskIsApproved() {
        createDefinition("it_add_sign", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_add_sign", "加签端到端");
        WfTask originalTask = onlyPendingTask(started.getInstance().getId());

        asUser(APPROVER_A);
        WfTaskActionRequest addSignRequest = action("请协助确认");
        addSignRequest.setAddSignUserIds(List.of(APPROVER_B));
        runtimeService.addSign(originalTask.getId(), addSignRequest);
        assertThat(pendingTasks(started.getInstance().getId())).extracting(WfTask::getAssigneeId)
                .containsExactly(APPROVER_A, APPROVER_B);

        runtimeService.approve(originalTask.getId(), action("原审批同意"));
        assertThat(instanceStatus(started)).isEqualTo("RUNNING");
        assertThat(pendingTasks(started.getInstance().getId())).extracting(WfTask::getAssigneeId)
                .containsExactly(APPROVER_B);

        asUser(APPROVER_B);
        runtimeService.approve(onlyPendingTask(started.getInstance().getId()).getId(), action("加签同意"));

        assertThat(instanceStatus(started)).isEqualTo("APPROVED");
        assertThat(eventActions(started.getInstance().getId())).contains("ADD_SIGN", "APPROVE");
    }

    @Test
    void shouldTransferPendingTaskAndLetNewAssigneeContinue() {
        createDefinition("it_transfer", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_transfer", "转办端到端");
        WfTask originalTask = onlyPendingTask(started.getInstance().getId());

        asUser(APPROVER_A);
        WfTaskActionRequest transferRequest = action("转给乙处理");
        transferRequest.setTargetUserId(APPROVER_B);
        runtimeService.transfer(originalTask.getId(), transferRequest);

        assertThat(historicTaskService.getById(originalTask.getId()).getStatus()).isEqualTo("TRANSFERRED");
        WfTask transferredTask = onlyPendingTask(started.getInstance().getId());
        assertThat(transferredTask.getAssigneeId()).isEqualTo(APPROVER_B);

        asUser(APPROVER_B);
        runtimeService.approve(transferredTask.getId(), action("转办后通过"));

        assertThat(instanceStatus(started)).isEqualTo("APPROVED");
        assertThat(eventActions(started.getInstance().getId())).contains("TRANSFER", "APPROVE");
    }

    @Test
    void shouldRejectTransferToInactiveOrMissingAssignee() {
        createDefinition("it_transfer_invalid_target", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_transfer_invalid_target", "非法转办目标");
        WfTask originalTask = onlyPendingTask(started.getInstance().getId());

        asUser(APPROVER_A);
        WfTaskActionRequest transferRequest = action("转给不存在的人");
        transferRequest.setTargetUserId(999_999L);

        assertThatThrownBy(() -> runtimeService.transfer(originalTask.getId(), transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标处理人不存在或已停用");
        assertThat(onlyPendingTask(started.getInstance().getId()).getAssigneeId()).isEqualTo(APPROVER_A);
    }

    @Test
    void shouldRejectTransferWhenApprovalNodeDisablesTransfer() {
        createDefinition("it_transfer_disabled", graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNodeWithPolicy("approve", "不可转办审批", "ANY_ONE", "{\"allowTransfer\":false}", APPROVER_A),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "approve"), edge("approve", "end"))));
        WfProcessInstanceDetail started = start("it_transfer_disabled", "禁用转办端到端");
        WfTask originalTask = onlyPendingTask(started.getInstance().getId());

        asUser(APPROVER_A);
        WfTaskActionRequest transferRequest = action("尝试转办");
        transferRequest.setTargetUserId(APPROVER_B);

        assertThatThrownBy(() -> runtimeService.transfer(originalTask.getId(), transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前节点不允许转办");
        assertThat(onlyPendingTask(started.getInstance().getId()).getAssigneeId()).isEqualTo(APPROVER_A);
    }

    @Test
    void shouldDelegatePendingTaskAndLetDelegateeContinue() {
        createDefinition("it_delegate", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_delegate", "委派端到端");
        WfTask originalTask = onlyPendingTask(started.getInstance().getId());

        asUser(APPROVER_A);
        WfTaskActionRequest delegateRequest = action("委派乙处理");
        delegateRequest.setTargetUserId(APPROVER_B);
        runtimeService.delegate(originalTask.getId(), delegateRequest);

        assertThat(historicTaskService.getById(originalTask.getId()).getStatus()).isEqualTo("DELEGATED");
        WfTask delegatedTask = onlyPendingTask(started.getInstance().getId());
        assertThat(delegatedTask.getAssigneeId()).isEqualTo(APPROVER_B);

        asUser(APPROVER_B);
        runtimeService.approve(delegatedTask.getId(), action("委派后通过"));

        assertThat(instanceStatus(started)).isEqualTo("APPROVED");
        assertThat(eventActions(started.getInstance().getId())).contains("DELEGATE", "APPROVE");
    }

    @Test
    void shouldNotifyCurrentAssigneeWhenInitiatorRemindsPendingTask() {
        createDefinition("it_remind_message", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_remind_message", "催办通知端到端");
        WfTask pendingTask = onlyPendingTask(started.getInstance().getId());

        asUser(INITIATOR);
        runtimeService.remind(pendingTask.getId(), action("请今天处理"));

        List<UserMessage> messages = userMessageService.lambdaQuery()
                .eq(UserMessage::getReceiverId, APPROVER_A)
                .eq(UserMessage::getCategory, "WORKFLOW")
                .eq(UserMessage::getBizType, "WORKFLOW_INSTANCE")
                .eq(UserMessage::getBizId, String.valueOf(started.getInstance().getId()))
                .list();
        assertThat(messages).hasSize(1);
        UserMessage message = messages.get(0);
        assertThat(message.getTitle()).contains("催办", "催办通知端到端");
        assertThat(message.getContent()).contains("用户7001", "审批节点", "请今天处理");
        assertThat(message.getLink()).isEqualTo("/workflow/tasks?tab=pending&instanceId=" + started.getInstance().getId());
        assertThat(eventActions(started.getInstance().getId())).contains("REMIND");
    }

    @Test
    void shouldReturnToSubmitNodeAndContinueAgain() {
        createDefinition("it_return", graph(nodes(
                node("start", "circle", "开始", "START"),
                node("submit", "rect", "提交申请", "SUBMIT"),
                approvalNode("leader", "负责人审批", "ANY_ONE", APPROVER_A),
                approvalNode("finance", "财务复核", "ANY_ONE", APPROVER_B),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", "submit"), edge("submit", "leader"), edge("leader", "finance"), edge("finance", "end"))));
        WfProcessInstanceDetail started = start("it_return", "退回端到端");

        asUser(APPROVER_A);
        runtimeService.approve(onlyPendingTask(started.getInstance().getId()).getId(), action("负责人同意"));

        asUser(APPROVER_B);
        WfTask financeTask = onlyPendingTask(started.getInstance().getId());
        WfTaskActionRequest returnRequest = action("退回补充资料");
        returnRequest.setReturnNodeKey("submit");
        runtimeService.returnTask(financeTask.getId(), returnRequest);

        WfTask submitTask = onlyPendingTask(started.getInstance().getId());
        assertThat(submitTask.getNodeKey()).isEqualTo("submit");
        assertThat(submitTask.getAssigneeId()).isEqualTo(INITIATOR);

        asUser(INITIATOR);
        runtimeService.approve(submitTask.getId(), action("补充后提交"));
        assertThat(onlyPendingTask(started.getInstance().getId()).getNodeKey()).isEqualTo("leader");
        assertThat(eventActions(started.getInstance().getId())).contains("RETURN");
    }

    @Test
    void shouldRevokeRunningInstanceAndCancelPendingTasks() {
        createDefinition("it_revoke", simpleApprovalGraph("approve", APPROVER_A));
        WfProcessInstanceDetail started = start("it_revoke", "撤回端到端");

        asUser(INITIATOR);
        WfInstanceActionRequest revokeRequest = new WfInstanceActionRequest();
        revokeRequest.setComment("发起人主动撤回");
        runtimeService.revoke(started.getInstance().getId(), revokeRequest);

        assertThat(instanceStatus(started)).isEqualTo("REVOKED");
        assertThat(taskStatuses(started.getInstance().getId())).containsExactly("CANCELED");
        assertThat(eventActions(started.getInstance().getId())).contains("REVOKE");
    }

    private WfProcessInstanceDetail start(String processKey, String title) {
        return start(processKey, title, null);
    }

    private WfProcessInstanceDetail start(String processKey, String title, Long assigneeId) {
        asUser(INITIATOR);
        WfStartProcessRequest request = new WfStartProcessRequest();
        request.setProcessKey(processKey);
        request.setBusinessType("workflow-test");
        request.setBusinessId(processKey + "-business");
        request.setTitle(title);
        request.setAssigneeId(assigneeId);
        request.setComment("提交测试流程");
        return runtimeService.start(request);
    }

    private void createDefinition(String processKey, String graphJson) {
        LocalDateTime now = LocalDateTime.now();
        WfProcessDefinition definition = new WfProcessDefinition();
        definition.setProcessKey(processKey);
        definition.setProcessName(processKey);
        definition.setCurrentVersion(1);
        definition.setStatus("ENABLED");
        definition.setCreatedBy(INITIATOR);
        definition.setCreatedAt(now);
        definition.setUpdatedBy(INITIATOR);
        definition.setUpdatedAt(now);
        definitionService.save(definition);

        WfProcessDefinitionVersion version = new WfProcessDefinitionVersion();
        version.setDefinitionId(definition.getId());
        version.setVersion(1);
        version.setGraphJson(graphJson);
        version.setStatus("PUBLISHED");
        version.setPublishedBy(INITIATOR);
        version.setPublishedAt(now);
        version.setCreatedBy(INITIATOR);
        version.setCreatedAt(now);
        version.setUpdatedBy(INITIATOR);
        version.setUpdatedAt(now);
        versionService.save(version);
    }

    private String simpleApprovalGraph(String nodeKey, long approverId) {
        return graph(nodes(
                node("start", "circle", "开始", "START"),
                approvalNode(nodeKey, "审批节点", "ANY_ONE", approverId),
                node("end", "circle", "结束", "END")
        ), edges(edge("start", nodeKey), edge(nodeKey, "end")));
    }

    private String graph(String nodes, String edges) {
        return "{\"nodes\":[" + nodes + "],\"edges\":[" + edges + "]}";
    }

    private String nodes(String... nodes) {
        return String.join(",", nodes);
    }

    private String edges(String... edges) {
        return String.join(",", edges);
    }

    private String node(String id, String type, String text, String nodeType) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"text\":\"" + text
                + "\",\"properties\":{\"nodeType\":\"" + nodeType + "\"}}";
    }

    private String approvalNode(String id, String text, String approveType, long... assigneeIds) {
        return "{\"id\":\"" + id + "\",\"type\":\"rect\",\"text\":\"" + text
                + "\",\"properties\":{\"nodeType\":\"APPROVAL\",\"approveType\":\"" + approveType
                + "\",\"approverType\":\"USER\",\"assigneeIds\":[" + ids(assigneeIds) + "]}}";
    }

    private String approvalNodeWithPolicy(String id, String text, String approveType, String policyJson, long... assigneeIds) {
        String policyBody = policyJson.substring(1, policyJson.length() - 1);
        return "{\"id\":\"" + id + "\",\"type\":\"rect\",\"text\":\"" + text
                + "\",\"properties\":{\"nodeType\":\"APPROVAL\",\"approveType\":\"" + approveType
                + "\",\"approverType\":\"USER\",\"assigneeIds\":[" + ids(assigneeIds) + "],"
                + policyBody + "}}";
    }

    private String approvalNodeByRule(String id, String text, String approveType, String approverType) {
        return "{\"id\":\"" + id + "\",\"type\":\"rect\",\"text\":\"" + text
                + "\",\"properties\":{\"nodeType\":\"APPROVAL\",\"approveType\":\"" + approveType
                + "\",\"approverType\":\"" + approverType + "\"}}";
    }

    private String edge(String source, String target) {
        return "{\"sourceNodeId\":\"" + source + "\",\"targetNodeId\":\"" + target
                + "\",\"type\":\"polyline\",\"properties\":{\"conditionType\":\"ALWAYS\"}}";
    }

    private String ids(long... ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids[i]);
        }
        return builder.toString();
    }

    private void asUser(long userId) {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(userId)
                .userName("u" + userId)
                .nickName("用户" + userId)
                .superAdmin(false)
                .build());
    }

    private void asDeptScopedUser(long userId, long deptId) {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(userId)
                .userName("u" + userId)
                .nickName("用户" + userId)
                .deptId(deptId)
                .deptIds(Set.of(deptId))
                .dataScopes(List.of(DataScopeType.DEPT))
                .superAdmin(false)
                .build());
    }

    private void saveWorkflowUser(long userId, String userName, String nickName, long deptId) {
        if (sysUserService.getById(userId) != null) {
            return;
        }
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setDeptId(deptId);
        user.setEnable(1);
        user.setDeleted(0);
        user.setVersion(0);
        user.setPermissionVersion(1L);
        user.setEmployeeNo("WF" + userId);
        sysUserService.save(user);
    }

    private void saveWorkflowDepartment(long deptId, String deptName, long parentDeptId, long leaderUserId) {
        SysDept department = sysDeptService.getById(deptId);
        if (department == null) {
            department = new SysDept();
            department.setDeptId(deptId);
            department.setDeleted(0);
            department.setVersion(0);
        }
        department.setDeptName(deptName);
        department.setFullName("易企科技有限公司 / " + deptName);
        department.setPid(parentDeptId);
        department.setTreePath("/202604280103000001/" + deptId + "/");
        department.setLeaderUserId(leaderUserId);
        department.setStatus(true);
        department.setSort(90);
        sysDeptService.saveOrUpdate(department);
    }

    private WfTaskActionRequest action(String comment) {
        WfTaskActionRequest request = new WfTaskActionRequest();
        request.setComment(comment);
        return request;
    }

    private List<WfTask> pendingTasks(Long instanceId) {
        return EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getStatus, "PENDING")
                .orderByAsc(WfTask::getStartedAt)
                .orderByAsc(WfTask::getId)
                .list());
    }

    private WfTask onlyPendingTask(Long instanceId) {
        List<WfTask> tasks = pendingTasks(instanceId);
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }

    private WfTask taskOf(List<WfTask> tasks, long assigneeId) {
        return tasks.stream()
                .filter(task -> task.getAssigneeId().equals(assigneeId))
                .findFirst()
                .orElseThrow();
    }

    private String instanceStatus(WfProcessInstanceDetail detail) {
        var instance = runtimeService.detail(detail.getInstance().getId()).getInstance();
        return instance.getStatus();
    }

    private List<String> taskStatuses(Long instanceId) {
        List<String> historicStatuses = historicTaskService.lambdaQuery()
                .eq(WfHistoricTask::getInstanceId, instanceId)
                .orderByAsc(WfHistoricTask::getStartedAt)
                .orderByAsc(WfHistoricTask::getId)
                .list()
                .stream()
                .map(WfHistoricTask::getStatus)
                .toList();
        List<String> activeStatuses = EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .orderByAsc(WfTask::getStartedAt)
                .orderByAsc(WfTask::getId)
                .list())
                .stream()
                .map(WfTask::getStatus)
                .toList();
        return java.util.stream.Stream.concat(historicStatuses.stream(), activeStatuses.stream()).toList();
    }

    private List<String> eventActions(Long instanceId) {
        return eventService.lambdaQuery()
                .eq(WfEvent::getInstanceId, instanceId)
                .orderByAsc(WfEvent::getCreatedAt)
                .orderByAsc(WfEvent::getId)
                .list()
                .stream()
                .map(WfEvent::getAction)
                .toList();
    }
}
