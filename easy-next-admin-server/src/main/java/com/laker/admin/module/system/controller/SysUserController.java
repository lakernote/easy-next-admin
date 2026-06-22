package com.laker.admin.module.system.controller;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.idempotency.duplicate.EasyDuplicateRequestLimiter;
import com.laker.admin.infrastructure.observability.metrics.EasyMetrics;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.annotation.EasyPermissionMode;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.system.dto.FlowAssigneVo;
import com.laker.admin.module.system.dto.SystemRoleView;
import com.laker.admin.module.system.dto.SystemUserQuery;
import com.laker.admin.module.system.dto.SystemUserView;
import com.laker.admin.module.system.dto.UserStatusRequest;
import com.laker.admin.module.system.dto.user.UserImportResult;
import com.laker.admin.module.system.dto.user.UserRequest;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.system.support.UserCsvCodec;

import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户管理接口。
 * Controller 只负责 HTTP 入参出参转换，用户保存、角色绑定、密码等业务规则统一下沉到 Service。
 */
@RestController
@RequestMapping("/api/system/users")
@EasyMetrics
public class SysUserController {

    private final ISysUserService sysUserService;

    public SysUserController(ISysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.System.USER_LIST)
    public PageResponse<SystemUserView> pageAll(SystemUserQuery query) {
        return sysUserService.pageUsers(query);
    }

    @PostMapping
    @EasyPermission(EasyPermissions.System.USER_ADD)
    @EasyAudit(module = "系统管理", action = "新增用户", dataChange = true, bizType = "SYS_USER", changeType = "CREATE")
    @EasyDuplicateRequestLimiter(
            businessKey = "system:user:create",
            businessParam = "#userRequest.userName",
            timeout = 2)
    public Response<SystemUserView> createUser(@RequestBody @Valid UserRequest userRequest) {
        return Response.ok(sysUserService.createUser(userRequest));
    }

    @PutMapping("/{id}")
    @EasyPermission(EasyPermissions.System.USER_EDIT)
    @EasyAudit(module = "系统管理", action = "修改用户", dataChange = true, bizType = "SYS_USER", bizId = "#id", changeType = "UPDATE")
    @EasyDuplicateRequestLimiter(
            businessKey = "system:user:update",
            businessParam = "#id",
            timeout = 2)
    public Response<SystemUserView> updateUser(@PathVariable Long id, @RequestBody @Valid UserRequest userRequest) {
        return Response.ok(sysUserService.updateUser(id, userRequest));
    }

    @GetMapping(value = "/import-template", produces = "text/csv;charset=UTF-8")
    @EasyPermission(EasyPermissions.System.USER_IMPORT)
    public ResponseEntity<byte[]> importTemplate() {
        return csvResponse(UserCsvCodec.IMPORT_TEMPLATE_FILE_NAME, sysUserService.userImportTemplate());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EasyPermission(EasyPermissions.System.USER_IMPORT)
    @EasyAudit(module = "系统管理", action = "导入用户", dataChange = true, bizType = "SYS_USER", changeType = "IMPORT")
    public Response<UserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        return Response.ok(sysUserService.importUsers(file));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @EasyPermission(EasyPermissions.System.USER_EXPORT)
    public ResponseEntity<byte[]> exportUsers(SystemUserQuery query) {
        return csvResponse(UserCsvCodec.EXPORT_FILE_NAME, sysUserService.exportUsers(query));
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.System.USER_LIST)
    public Response<SystemUserView> get(@PathVariable Long id) {
        SystemUserView user = sysUserService.getUserAndDeptById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return Response.ok(user);
    }

    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.System.USER_DELETE)
    @EasyAudit(module = "系统管理", action = "删除用户", dataChange = true, bizType = "SYS_USER", bizId = "#id", changeType = "DELETE")
    public Response<Boolean> delete(@PathVariable Long id) {
        return Response.ok(sysUserService.deleteUser(id));
    }

    @DeleteMapping("/batch/{ids}")
    @EasyPermission(EasyPermissions.System.USER_DELETE)
    @EasyAudit(module = "系统管理", action = "批量删除用户", dataChange = true, bizType = "SYS_USER", changeType = "BATCH_DELETE")
    public Response<Boolean> batchRemove(@PathVariable Long[] ids) {
        return Response.ok(sysUserService.deleteUsers(Arrays.asList(ids)));
    }

    @PutMapping("/switch")
    @EasyPermission(EasyPermissions.System.USER_EDIT)
    @EasyAudit(module = "系统管理", action = "切换用户状态", dataChange = true, bizType = "SYS_USER", changeType = "STATUS")
    public Response<Boolean> userSwitch(@RequestBody @Valid UserStatusRequest param) {
        return Response.ok(sysUserService.switchUserStatus(param));
    }

    @GetMapping("/assignable-roles")
    @EasyPermission(EasyPermissions.System.USER_LIST)
    public Response<List<SystemRoleView>> assignableRoles(@RequestParam(required = false) Long userId) {
        return Response.ok(sysUserService.listAssignableRoles(userId));
    }

    @GetMapping("/assignees")
    @EasyPermission(value = {EasyPermissions.System.USER_LIST, EasyPermissions.Workflow.VIEW}, mode = EasyPermissionMode.ANY)
    public Response<List<FlowAssigneVo>> assignees(@RequestParam(required = false) Long userId) {
        return Response.ok(sysUserService.listWorkflowAssignees(userId));
    }

    @PutMapping("/resetPwd/{userId}")
    @EasyPermission(EasyPermissions.System.USER_RESET_PASSWORD)
    @EasyAudit(module = "系统管理", action = "重置用户密码", dataChange = true, bizType = "SYS_USER", bizId = "#userId", changeType = "RESET_PASSWORD")
    public Response<Boolean> resetPwd(@PathVariable Long userId) {
        return Response.ok(sysUserService.resetPassword(userId));
    }

    private ResponseEntity<byte[]> csvResponse(String fileName, byte[] bytes) {
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
}
