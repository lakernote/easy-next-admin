package com.laker.admin.module.business.number.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.business.number.dto.BusinessNumberGeneratedView;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleQuery;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleRequest;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleView;
import com.laker.admin.module.business.number.service.BusinessNumberService;
import com.laker.admin.module.business.number.service.IBusinessNumberRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/business-numbers/rules")
@EasyApiAccessLog
public class BusinessNumberRuleController {
    private final IBusinessNumberRuleService ruleService;
    private final BusinessNumberService businessNumberService;

    public BusinessNumberRuleController(IBusinessNumberRuleService ruleService,
                                        BusinessNumberService businessNumberService) {
        this.ruleService = ruleService;
        this.businessNumberService = businessNumberService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.BusinessNumber.LIST)
    public PageResponse<BusinessNumberRuleView> page(BusinessNumberRuleQuery query) {
        return ruleService.pageRules(query);
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.BusinessNumber.LIST)
    public Response<BusinessNumberRuleView> get(@PathVariable Long id) {
        return Response.ok(ruleService.getRule(id));
    }

    @PostMapping
    @EasyPermission(EasyPermissions.BusinessNumber.EDIT)
    @EasyAudit(module = "业务编号", action = "保存编号规则", dataChange = true, bizType = "BUSINESS_NUMBER_RULE", changeType = "SAVE")
    public Response<BusinessNumberRuleView> save(@RequestBody @Valid BusinessNumberRuleRequest request) {
        return Response.ok(ruleService.saveRule(request));
    }

    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.BusinessNumber.EDIT)
    @EasyAudit(module = "业务编号", action = "删除编号规则", dataChange = true, bizType = "BUSINESS_NUMBER_RULE", bizId = "#id", changeType = "DELETE")
    public Response<Boolean> delete(@PathVariable Long id) {
        return Response.ok(ruleService.deleteRule(id));
    }

    @PostMapping("/{ruleCode}/generate")
    @EasyPermission(EasyPermissions.BusinessNumber.GENERATE)
    @EasyAudit(module = "业务编号", action = "人工生成编号", dataChange = true, bizType = "BUSINESS_NUMBER", bizId = "#ruleCode", changeType = "GENERATE")
    public Response<BusinessNumberGeneratedView> generate(@PathVariable String ruleCode) {
        String number = businessNumberService.nextNumber(ruleCode);
        return Response.ok(BusinessNumberGeneratedView.builder()
                .ruleCode(ruleCode)
                .number(number)
                .generatedAt(LocalDateTime.now())
                .build());
    }
}
