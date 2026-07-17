package com.laker.admin.module.system.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.system.dto.SystemDepartmentRequest;
import com.laker.admin.module.system.dto.SystemDepartmentView;
import com.laker.admin.module.system.dto.SystemDeptQuery;
import com.laker.admin.module.system.service.ISysDeptService;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Arrays;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织架构接口。
 * Controller 只暴露组织树、分页、增删改入口，组织查询规则统一放在 Service。
 */
@RestController
@RequestMapping("/api/system/departments")
@EasyApiAccessLog
public class SysDeptController {

    private final ISysDeptService sysDeptService;

    public SysDeptController(ISysDeptService sysDeptService) {
        this.sysDeptService = sysDeptService;
    }

    @GetMapping
    @Operation(summary = "分页查询")
    @EasyPermission(EasyPermissions.System.DEPT_LIST)
    public PageResponse<SystemDepartmentView> pageAll(SystemDeptQuery query) {
        return sysDeptService.pageDepartments(query);
    }

    @PostMapping
    @Operation(summary = "新增或者更新")
    @EasyPermission(EasyPermissions.System.DEPT_EDIT)
    @EasyAudit(module = "系统管理", action = "保存部门", dataChange = true, bizType = "SYS_DEPT", changeType = "SAVE")
    public Response<Boolean> saveOrUpdate(@RequestBody @Valid SystemDepartmentRequest param) {
        return Response.ok(sysDeptService.saveDepartment(param.toEntity()));
    }

    @GetMapping("/{id:[0-9]+}")
    @Operation(summary = "根据id查询")
    @EasyPermission(EasyPermissions.System.DEPT_LIST)
    public Response<SystemDepartmentView> get(@PathVariable Long id) {
        return Response.ok(SystemDepartmentView.from(sysDeptService.getById(id)));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @Operation(summary = "根据id删除")
    @EasyPermission(EasyPermissions.System.DEPT_EDIT)
    @EasyAudit(module = "系统管理", action = "删除部门", dataChange = true, bizType = "SYS_DEPT", bizId = "#id", changeType = "DELETE")
    public Response<Boolean> delete(@PathVariable Long id) {
        return Response.ok(sysDeptService.deleteDepartment(id));
    }

    @DeleteMapping("/batch/{ids}")
    @Operation(summary = "根据批量删除ids删除")
    @EasyPermission(EasyPermissions.System.DEPT_EDIT)
    @EasyAudit(module = "系统管理", action = "批量删除部门", dataChange = true, bizType = "SYS_DEPT", changeType = "BATCH_DELETE")
    public Response<Boolean> batchRemove(@PathVariable Long[] ids) {
        return Response.ok(sysDeptService.deleteDepartments(Arrays.asList(ids)));
    }

    @GetMapping("/tree")
    @EasyPermission(EasyPermissions.System.DEPT_LIST)
    public Response<List<SystemDepartmentView>> tree() {
        return Response.ok(SystemDepartmentView.fromList(sysDeptService.tree()));
    }

}
