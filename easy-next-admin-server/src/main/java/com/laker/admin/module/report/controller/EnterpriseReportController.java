package com.laker.admin.module.report.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.report.dto.EnterpriseReportOverview;
import com.laker.admin.module.report.service.EnterpriseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "企业报表")
@RestController
@RequestMapping("/api/reports")
public class EnterpriseReportController {
    private final EnterpriseReportService reportService;

    public EnterpriseReportController(EnterpriseReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/enterprise-paper")
    @EasyPermission(EasyPermissions.Report.VIEW)
    @Operation(summary = "查询纸质企业报表")
    public Response<EnterpriseReportOverview> enterprisePaperReport() {
        return Response.ok(reportService.overview());
    }
}
