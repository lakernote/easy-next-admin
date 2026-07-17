package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.workflow.dto.WfCcListItem;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.support.WfCcQueryBuilder;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "流程抄送")
@RestController
@RequestMapping("/api/workflow/cc")
public class WfCcController {
    private final IWfCcService ccService;
    private final IWfHistoricCcService historicCcService;
    private final WorkflowArchiveService archiveService;
    private final UserMessageService userMessageService;

    public WfCcController(IWfCcService ccService,
                          IWfHistoricCcService historicCcService,
                          WorkflowArchiveService archiveService,
                          UserMessageService userMessageService) {
        this.ccService = ccService;
        this.historicCcService = historicCcService;
        this.archiveService = archiveService;
        this.userMessageService = userMessageService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @Operation(summary = "分页查询流程抄送")
    public PageResponse<WfCcListItem> page(@RequestParam(required = false, defaultValue = "1") long page,
                                                 @RequestParam(required = false, defaultValue = "10") long limit,
                                                 @RequestParam(required = false) Integer readStatus,
                                                 @RequestParam(required = false, defaultValue = "false") boolean mine) {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        LambdaQueryWrapper<WfCc> queryWrapper = WfCcQueryBuilder.build(principal, readStatus, mine);
        LambdaQueryWrapper<WfHistoricCc> historicQueryWrapper = WfCcQueryBuilder.buildHistoric(principal, readStatus, mine);
        if (mine) {
            Long userId = EasySecurityContext.getUserId();
            if (userId == null) {
                throw new BusinessException("未获取到当前登录用户");
            }
        }
        long total = ccService.count(queryWrapper) + historicCcService.count(historicQueryWrapper);
        long fetchSize = page * limit;
        List<CcRecord> ccList = new ArrayList<>(ccService.page(new Page<>(1, fetchSize), queryWrapper).getRecords().stream()
                .map(cc -> new CcRecord(cc, false))
                .toList());
        ccList.addAll(historicCcService.page(new Page<>(1, fetchSize), historicQueryWrapper).getRecords().stream()
                .map(archiveService::toRuntimeCc)
                .map(cc -> new CcRecord(cc, true))
                .toList());
        ccList.sort(Comparator.comparing((CcRecord record) -> record.cc().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(record -> record.cc().getId(), Comparator.nullsLast(Comparator.<Long>naturalOrder()).reversed()));
        List<CcRecord> recordsForPage = ccList.stream()
                .skip((page - 1) * limit)
                .limit(limit)
                .toList();
        Map<Long, WfProcessInstance> instanceMap = instanceMap(recordsForPage);
        List<WfCcListItem> records = recordsForPage.stream()
                .map(record -> WfCcListItem.from(record.cc(), instanceMap.get(record.cc().getInstanceId()), record.historic()))
                .toList();
        return PageResponse.ok(records, total);
    }

    private Map<Long, WfProcessInstance> instanceMap(List<CcRecord> ccList) {
        List<Long> instanceIds = ccList.stream()
                .map(record -> record.cc().getInstanceId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        return archiveService.instanceMap(instanceIds);
    }

    @PutMapping("/{id}/read")
    @EasyPermission(EasyPermissions.Workflow.VIEW)
    @EasyAudit(module = "流程抄送", action = "标记抄送已读", dataChange = true, bizType = "WORKFLOW_CC", bizId = "#id", changeType = "READ")
    @Operation(summary = "标记抄送已读")
    public Response<Boolean> markRead(@PathVariable Long id,
                                      @RequestParam(required = false, defaultValue = "false") boolean historic) {
        if (historic) {
            WfHistoricCc historicCc = historicCcService.getById(id);
            if (historicCc == null) {
                throw new BusinessException("流程抄送不存在");
            }
            ensureCanMarkRead(historicCc.getReceiverId());
            historicCc.setReadStatus(1);
            historicCc.setReadAt(LocalDateTime.now());
            boolean updated = historicCcService.updateById(historicCc);
            userMessageService.markWorkflowCcMessageRead(historicCc.getReceiverId(), historicCc.getId());
            return Response.ok(updated);
        }
        WfCc cc = ccService.getById(id);
        if (cc == null) {
            throw new BusinessException("流程抄送不存在");
        }
        ensureCanMarkRead(cc.getReceiverId());
        cc.setReadStatus(1);
        cc.setReadAt(LocalDateTime.now());
        boolean updated = ccService.updateById(cc);
        userMessageService.markWorkflowCcMessageRead(cc.getReceiverId(), cc.getId());
        return Response.ok(updated);
    }

    private record CcRecord(WfCc cc, boolean historic) {
    }

    private void ensureCanMarkRead(Long receiverId) {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal != null && (principal.isSuperAdmin() || Objects.equals(principal.getUserId(), receiverId))) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "只能标记自己的流程抄送已读");
    }
}
