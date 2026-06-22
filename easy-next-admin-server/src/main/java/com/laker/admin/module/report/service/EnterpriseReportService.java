package com.laker.admin.module.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.report.dto.EnterpriseReportOverview;
import com.laker.admin.module.system.dto.SystemUserView;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.system.service.SysUserRelationService;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.purchase.entity.BizPurchaseRequest;
import com.laker.admin.module.workflow.purchase.service.IPurchaseRequestService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnterpriseReportService {
    private static final int USER_REPORT_LIMIT = 200;
    private static final int PURCHASE_REPORT_LIMIT = 10;
    private static final String ORGANIZATION_NAME = "易企科技有限公司";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月");

    private final SysUserMapper sysUserMapper;
    private final SysUserRelationService userRelationService;
    private final IPurchaseRequestService purchaseRequestService;
    private final IWfProcessInstanceService workflowInstanceService;
    private final EasyAuthService easyAuthService;

    public EnterpriseReportService(SysUserMapper sysUserMapper,
                                   SysUserRelationService userRelationService,
                                   IPurchaseRequestService purchaseRequestService,
                                   IWfProcessInstanceService workflowInstanceService,
                                   EasyAuthService easyAuthService) {
        this.sysUserMapper = sysUserMapper;
        this.userRelationService = userRelationService;
        this.purchaseRequestService = purchaseRequestService;
        this.workflowInstanceService = workflowInstanceService;
        this.easyAuthService = easyAuthService;
    }

    public EnterpriseReportOverview overview() {
        AuthPrincipal principal = easyAuthService.currentPrincipal();
        LocalDate today = LocalDate.now();
        List<SystemUserView> users = loadVisibleUsers();

        EnterpriseReportOverview overview = new EnterpriseReportOverview();
        overview.setOrganizationName(ORGANIZATION_NAME);
        overview.setReportPeriod(PERIOD_FORMATTER.format(today));
        overview.setGeneratedAt(TIME_FORMATTER.format(LocalDateTime.now()));
        overview.setPreparedBy(displayName(principal));
        overview.setDataScopeLabel(dataScopeLabel(principal));
        overview.setOrganizationLedger(buildOrganizationLedger(users, today, principal));
        overview.setPurchaseReview(buildPurchaseReview(users, today));
        return overview;
    }

    private List<SystemUserView> loadVisibleUsers() {
        Page<SysUser> page = sysUserMapper.selectPage(
                new Page<>(1, USER_REPORT_LIMIT, false),
                Wrappers.<SysUser>lambdaQuery()
                        .orderByAsc(SysUser::getDeptId)
                        .orderByAsc(SysUser::getEmployeeNo)
                        .orderByAsc(SysUser::getUserId)
        );
        return userRelationService.toUserViews(page.getRecords());
    }

    private EnterpriseReportOverview.OrganizationLedgerReport buildOrganizationLedger(List<SystemUserView> users,
                                                                                      LocalDate today,
                                                                                      AuthPrincipal principal) {
        EnterpriseReportOverview.OrganizationLedgerReport report = new EnterpriseReportOverview.OrganizationLedgerReport();
        report.setReportNo("ORG-" + today.format(DateTimeFormatter.BASIC_ISO_DATE));
        report.setRows(departmentRows(users));
        report.setMetrics(List.of(
                metric("可见部门", String.valueOf(report.getRows().size())),
                metric("人员总数", String.valueOf(users.size())),
                metric("启用账号", String.valueOf(users.stream().filter(user -> Objects.equals(user.getEnable(), 1)).count())),
                metric("数据口径", dataScopeLabel(principal))
        ));
        report.setSignatures(defaultSignatures("组织负责人", "人事复核"));
        return report;
    }

    private List<EnterpriseReportOverview.DepartmentLedgerRow> departmentRows(List<SystemUserView> users) {
        Map<DepartmentGroupKey, List<SystemUserView>> usersByDept = users.stream()
                .sorted(Comparator.comparing((SystemUserView user) -> nullSafe(user.getDeptName()))
                        .thenComparing(user -> nullSafe(user.getEmployeeNo()))
                        .thenComparing(SystemUserView::getUserId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.groupingBy(
                        this::departmentGroupKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int[] index = {1};
        return usersByDept.entrySet().stream()
                .map(entry -> {
                    EnterpriseReportOverview.DepartmentLedgerRow row = new EnterpriseReportOverview.DepartmentLedgerRow();
                    List<SystemUserView> deptUsers = entry.getValue();
                    row.setIndex(index[0]++);
                    row.setDepartmentName(entry.getKey().departmentName());
                    row.setLeaderName(firstText(deptUsers.stream().map(SystemUserView::getDepartmentLeaderName).toList()));
                    row.setUserCount(deptUsers.size());
                    row.setEnabledCount((int) deptUsers.stream().filter(user -> Objects.equals(user.getEnable(), 1)).count());
                    row.setManagerSummary(compactDistinct(deptUsers.stream().map(SystemUserView::getManagerName).toList(), "未维护"));
                    row.setPositionSummary(positionSummary(deptUsers));
                    row.setLastLoginSummary(lastLoginSummary(deptUsers));
                    return row;
                })
                .toList();
    }

    private EnterpriseReportOverview.PurchaseReviewReport buildPurchaseReview(List<SystemUserView> users, LocalDate today) {
        List<EnterpriseReportOverview.PurchaseReviewRow> rows = purchaseRows(users);
        BigDecimal totalAmount = rows.stream()
                .map(EnterpriseReportOverview.PurchaseReviewRow::getEstimatedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        EnterpriseReportOverview.PurchaseReviewReport report = new EnterpriseReportOverview.PurchaseReviewReport();
        report.setReportNo("PUR-" + today.format(DateTimeFormatter.BASIC_ISO_DATE));
        report.setRows(rows);
        report.setMetrics(List.of(
                metric("采购单数", String.valueOf(rows.size())),
                metric("预算金额", totalAmount.toPlainString()),
                metric("审批中", String.valueOf(rows.stream().filter(row -> "审批中".equals(row.getStatusText())).count())),
                metric("已通过", String.valueOf(rows.stream().filter(row -> "已通过".equals(row.getStatusText())).count()))
        ));
        report.setSignatures(defaultSignatures("部门负责人", "财务复核"));
        return report;
    }

    private List<EnterpriseReportOverview.PurchaseReviewRow> purchaseRows(List<SystemUserView> users) {
        Map<Long, SystemUserView> userById = users.stream()
                .filter(user -> user.getUserId() != null)
                .collect(Collectors.toMap(SystemUserView::getUserId, user -> user, (left, right) -> left));
        if (userById.isEmpty()) {
            return List.of();
        }

        Page<BizPurchaseRequest> page = purchaseRequestService.page(
                new Page<>(1, PURCHASE_REPORT_LIMIT, false),
                Wrappers.<BizPurchaseRequest>lambdaQuery()
                        .in(BizPurchaseRequest::getApplicantId, userById.keySet())
                        .orderByDesc(BizPurchaseRequest::getCreatedAt)
                        .orderByDesc(BizPurchaseRequest::getId)
        );
        List<BizPurchaseRequest> purchases = page.getRecords();
        Map<Long, WfProcessInstance> instanceById = workflowInstanceMap(purchases);

        int[] index = {1};
        return purchases.stream()
                .map(purchase -> {
                    SystemUserView applicant = userById.get(purchase.getApplicantId());
                    WfProcessInstance instance = instanceById.get(purchase.getWorkflowInstanceId());
                    EnterpriseReportOverview.PurchaseReviewRow row = new EnterpriseReportOverview.PurchaseReviewRow();
                    row.setIndex(index[0]++);
                    row.setRequestNo(purchase.getRequestNo());
                    row.setApplicantName(applicant == null ? "-" : displayName(applicant));
                    row.setDepartmentName(applicant == null ? "-" : defaultText(applicant.getDeptName(), "未分配部门"));
                    row.setItemName(defaultText(purchase.getItemName(), "-"));
                    row.setCategory(defaultText(purchase.getCategory(), "-"));
                    row.setQuantity(purchase.getQuantity());
                    row.setEstimatedAmount(purchase.getEstimatedAmount());
                    row.setRequiredDate(purchase.getRequiredDate() == null ? "-" : DATE_FORMATTER.format(purchase.getRequiredDate()));
                    row.setStatusText(statusText(instance == null ? purchase.getStatus() : instance.getStatus()));
                    row.setCurrentNodeName(nodeName(instance == null ? null : instance.getCurrentNodeKey()));
                    return row;
                })
                .toList();
    }

    private Map<Long, WfProcessInstance> workflowInstanceMap(List<BizPurchaseRequest> purchases) {
        List<Long> instanceIds = purchases.stream()
                .map(BizPurchaseRequest::getWorkflowInstanceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        return workflowInstanceService.listByIds(instanceIds).stream()
                .collect(Collectors.toMap(WfProcessInstance::getId, instance -> instance, (left, right) -> left));
    }

    private EnterpriseReportOverview.ReportMetric metric(String label, String value) {
        EnterpriseReportOverview.ReportMetric metric = new EnterpriseReportOverview.ReportMetric();
        metric.setLabel(label);
        metric.setValue(value);
        return metric;
    }

    private List<EnterpriseReportOverview.SignatureCell> defaultSignatures(String first, String second) {
        return List.of(signature(first), signature(second), signature("制表人"), signature("归档日期"));
    }

    private EnterpriseReportOverview.SignatureCell signature(String label) {
        EnterpriseReportOverview.SignatureCell cell = new EnterpriseReportOverview.SignatureCell();
        cell.setLabel(label);
        cell.setValue("");
        return cell;
    }

    private DepartmentGroupKey departmentGroupKey(SystemUserView user) {
        return new DepartmentGroupKey(
                user.getDeptId(),
                defaultText(user.getDeptName(), "未分配部门")
        );
    }

    private String positionSummary(List<SystemUserView> users) {
        return users.stream()
                .collect(Collectors.groupingBy(
                        user -> defaultText(user.getPositionName(), "未维护岗位"),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(" / "));
    }

    private String lastLoginSummary(List<SystemUserView> users) {
        long loggedIn = users.stream().filter(user -> user.getLastLoginTime() != null).count();
        String latest = users.stream()
                .map(SystemUserView::getLastLoginTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(TIME_FORMATTER::format)
                .orElse("-");
        return "已登录 " + loggedIn + "/" + users.size() + "，最近 " + latest;
    }

    private String compactDistinct(List<String> values, String fallback) {
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(" / "));
        return StringUtils.hasText(joined) ? joined : fallback;
    }

    private String firstText(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("未维护");
    }

    private String dataScopeLabel(AuthPrincipal principal) {
        if (principal == null || principal.getDataScopes() == null || principal.getDataScopes().isEmpty()) {
            return "本人";
        }
        return principal.getDataScopes().stream()
                .map(DataScopeType::getLabel)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    private String displayName(AuthPrincipal principal) {
        if (principal == null) {
            return "-";
        }
        return defaultText(principal.getNickName(), defaultText(principal.getUserName(), "-"));
    }

    private String displayName(SystemUserView user) {
        return defaultText(user.getRealName(), defaultText(user.getNickName(), defaultText(user.getUserName(), "-")));
    }

    private String statusText(String status) {
        if ("RUNNING".equals(status) || "APPROVING".equals(status)) {
            return "审批中";
        }
        if ("APPROVED".equals(status)) {
            return "已通过";
        }
        if ("REJECTED".equals(status)) {
            return "已驳回";
        }
        if ("REVOKED".equals(status)) {
            return "已撤回";
        }
        if ("TERMINATED".equals(status)) {
            return "已终止";
        }
        if ("DRAFT".equals(status)) {
            return "草稿";
        }
        return defaultText(status, "-");
    }

    private String nodeName(String nodeKey) {
        if ("manager".equals(nodeKey)) {
            return "负责人审批";
        }
        if ("finance".equals(nodeKey)) {
            return "财务复核";
        }
        if ("office".equals(nodeKey)) {
            return "总经办审批";
        }
        if ("end".equals(nodeKey)) {
            return "结束";
        }
        return defaultText(nodeKey, "-");
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record DepartmentGroupKey(Long departmentId, String departmentName) {
    }
}
