package com.laker.admin.module.system.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.system.dto.workbench.EnterpriseWorkbenchOverview;
import com.laker.admin.module.system.service.EnterpriseWorkbenchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统-企业工作台")
@RestController
@RequestMapping("/api/system/workbench")
public class EnterpriseWorkbenchController {
    private final EnterpriseWorkbenchService enterpriseWorkbenchService;

    public EnterpriseWorkbenchController(EnterpriseWorkbenchService enterpriseWorkbenchService) {
        this.enterpriseWorkbenchService = enterpriseWorkbenchService;
    }

    @GetMapping("/overview")
    @EasyPermission(EasyPermissions.Dashboard.VIEW)
    public Response<EnterpriseWorkbenchOverview> overview() {
        return Response.ok(enterpriseWorkbenchService.buildOverview());
    }
}
