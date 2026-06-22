package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.security.support.EasyPasswordHasher;
import com.laker.admin.module.system.dto.SystemUserQuery;
import com.laker.admin.module.system.dto.user.UserBO;
import com.laker.admin.module.system.dto.user.UserImportResult;
import com.laker.admin.module.system.dto.user.UserRequest;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysRole;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.entity.SysUserRole;
import com.laker.admin.module.system.mapper.SysDeptMapper;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.system.mapstruct.UserBeanMap;
import com.laker.admin.module.system.support.UserCsvCodec;
import com.laker.admin.module.system.support.UserQuerySupport;
import com.laker.admin.module.system.support.UserRequestNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysUserImportExportService {
    private static final long MAX_IMPORT_FILE_SIZE = 2 * 1024 * 1024L;
    private static final int MAX_IMPORT_ROWS = 1000;
    private static final Set<String> ALLOWED_IMPORT_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "text/plain",
            "application/octet-stream"
    );

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper deptMapper;
    private final ISysRoleService sysRoleService;
    private final ISysUserRoleService sysUserRoleService;
    private final SystemUserAssignmentGuard assignmentGuard;
    private final SysUserRelationService userRelationService;
    private final UserBeanMap userBeanMap;
    private final EasyNextAdminConfig easyNextAdminConfig;
    private final EasyPasswordHasher passwordHasher;
    private final TransactionTemplate transactionTemplate;

    public SysUserImportExportService(SysUserMapper sysUserMapper,
                                      SysDeptMapper deptMapper,
                                      ISysRoleService sysRoleService,
                                      ISysUserRoleService sysUserRoleService,
                                      SystemUserAssignmentGuard assignmentGuard,
                                      SysUserRelationService userRelationService,
                                      UserBeanMap userBeanMap,
                                      EasyNextAdminConfig easyNextAdminConfig,
                                      EasyPasswordHasher passwordHasher,
                                      TransactionTemplate transactionTemplate) {
        this.sysUserMapper = sysUserMapper;
        this.deptMapper = deptMapper;
        this.sysRoleService = sysRoleService;
        this.sysUserRoleService = sysUserRoleService;
        this.assignmentGuard = assignmentGuard;
        this.userRelationService = userRelationService;
        this.userBeanMap = userBeanMap;
        this.easyNextAdminConfig = easyNextAdminConfig;
        this.passwordHasher = passwordHasher;
        this.transactionTemplate = transactionTemplate;
    }

    public byte[] userImportTemplate() {
        return UserCsvCodec.importTemplateBytes();
    }

    public UserImportResult importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要导入的用户 CSV 文件");
        }
        validateImportFile(file);
        List<UserCsvCodec.ImportRow> rows = parseImportRows(file);
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多导入 " + MAX_IMPORT_ROWS + " 行用户");
        }
        UserImportResult result = new UserImportResult();
        result.setTotalRows(rows.size());
        if (CollectionUtils.isEmpty(rows)) {
            return result;
        }

        Map<String, SysDept> deptByName = loadDepartments(rows);
        Map<String, SysRole> roleByCode = loadRoles(rows);
        Set<String> existingUserNames = loadExistingUserNames(rows);
        Set<String> importedUserNames = new LinkedHashSet<>();

        for (UserCsvCodec.ImportRow row : rows) {
            List<String> errors = validateImportRow(row, deptByName, roleByCode, existingUserNames, importedUserNames);
            if (!errors.isEmpty()) {
                result.addError(row.rowNumber(), row.userName(), String.join("；", errors));
                continue;
            }
            try {
                createImportedUser(toUserRequest(row, deptByName, roleByCode));
                importedUserNames.add(row.userName());
                result.markSuccess();
            } catch (RuntimeException ex) {
                result.addError(row.rowNumber(), row.userName(), importErrorMessage(ex));
            }
        }
        return result;
    }

    public byte[] exportUsers(SystemUserQuery query) {
        List<SysUser> users = sysUserMapper.selectList(UserQuerySupport.buildUserQuery(query).orderByAsc(SysUser::getUserName));
        userRelationService.fillUserRelations(users);
        Map<Long, SysDept> deptById = userRelationService.loadDepartmentMap(users);
        Map<Long, SysRole> roleById = sysRoleService.list().stream()
                .collect(Collectors.toMap(SysRole::getRoleId, role -> role, (left, right) -> left));
        List<UserCsvCodec.ExportRow> rows = users.stream()
                .map(user -> toExportRow(user, deptById, roleById))
                .toList();
        return UserCsvCodec.exportRows(rows);
    }

    private List<UserCsvCodec.ImportRow> parseImportRows(MultipartFile file) {
        try {
            return UserCsvCodec.parseImportRows(file.getBytes());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取导入文件失败", ex);
        }
    }

    private void validateImportFile(MultipartFile file) {
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入文件不能超过 2MB");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持导入 .csv 文件");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !ALLOWED_IMPORT_CONTENT_TYPES.contains(normalizeContentType(contentType))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入文件类型不正确，请上传 CSV 文件");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        int parameterStart = normalized.indexOf(';');
        if (parameterStart >= 0) {
            return normalized.substring(0, parameterStart).trim();
        }
        return normalized;
    }

    private Map<String, SysDept> loadDepartments(List<UserCsvCodec.ImportRow> rows) {
        List<String> departmentNames = rows.stream()
                .map(UserCsvCodec.ImportRow::deptName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(departmentNames)) {
            return Map.of();
        }
        Map<String, SysDept> departments = new LinkedHashMap<>();
        deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                        .and(wrapper -> wrapper.in(SysDept::getDeptName, departmentNames)
                                .or()
                                .in(SysDept::getFullName, departmentNames)))
                .forEach(dept -> {
                    putDepartment(departments, dept.getDeptName(), dept);
                    putDepartment(departments, dept.getFullName(), dept);
                });
        return departments;
    }

    private void putDepartment(Map<String, SysDept> departments, String key, SysDept dept) {
        if (StringUtils.hasText(key)) {
            departments.putIfAbsent(key.trim(), dept);
        }
    }

    private Map<String, SysRole> loadRoles(List<UserCsvCodec.ImportRow> rows) {
        List<String> roleCodes = rows.stream()
                .flatMap(row -> UserQuerySupport.splitBusinessCodes(row.roleCodes()).stream())
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(roleCodes)) {
            return Map.of();
        }
        return sysRoleService.list(Wrappers.<SysRole>lambdaQuery().in(SysRole::getRoleCode, roleCodes))
                .stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, role -> role, (left, right) -> left));
    }

    private Set<String> loadExistingUserNames(List<UserCsvCodec.ImportRow> rows) {
        List<String> userNames = rows.stream()
                .map(UserCsvCodec.ImportRow::userName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(userNames)) {
            return Set.of();
        }
        return sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserName, userNames)).stream()
                .map(SysUser::getUserName)
                .collect(Collectors.toSet());
    }

    private List<String> validateImportRow(UserCsvCodec.ImportRow row,
                                           Map<String, SysDept> deptByName,
                                           Map<String, SysRole> roleByCode,
                                           Set<String> existingUserNames,
                                           Set<String> importedUserNames) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(row.userName())) {
            errors.add("用户名不能为空");
        } else if (existingUserNames.contains(row.userName()) || importedUserNames.contains(row.userName())) {
            errors.add("用户名已存在");
        }
        if (!StringUtils.hasText(row.nickName())) {
            errors.add("姓名不能为空");
        }
        if (!StringUtils.hasText(row.deptName())) {
            errors.add("部门名称不能为空");
        } else {
            SysDept dept = deptByName.get(row.deptName().trim());
            if (dept == null || Boolean.FALSE.equals(dept.getStatus())) {
                errors.add("部门名称不存在或已停用");
            } else {
                addValidationError(errors, () -> assignmentGuard.validateAssignableDepartment(dept.getDeptId(), true));
            }
        }
        List<String> roleCodes = UserQuerySupport.splitBusinessCodes(row.roleCodes());
        if (roleCodes.isEmpty()) {
            errors.add("角色编码不能为空");
        } else {
            List<String> missingRoles = roleCodes.stream()
                    .filter(roleCode -> !roleByCode.containsKey(roleCode) || Boolean.FALSE.equals(roleByCode.get(roleCode).getEnable()))
                    .toList();
            if (!missingRoles.isEmpty()) {
                errors.add("角色编码不存在或已停用：" + String.join("|", missingRoles));
            } else {
                addValidationError(errors, () -> assignmentGuard.validateAssignableRoleIds(roleCodes.stream()
                        .map(roleCode -> roleByCode.get(roleCode).getRoleId())
                        .toList()));
            }
        }
        if (parseEnable(row.enable()) == null) {
            errors.add("启用只能填写 1、0、启用、停用或禁用");
        }
        return errors;
    }

    private void addValidationError(List<String> errors, Runnable validator) {
        try {
            validator.run();
        } catch (BusinessException ex) {
            errors.add(ex.getMessage());
        }
    }

    private UserRequest toUserRequest(UserCsvCodec.ImportRow row, Map<String, SysDept> deptByName, Map<String, SysRole> roleByCode) {
        UserRequest request = new UserRequest();
        request.setUserName(row.userName());
        request.setNickName(row.nickName());
        request.setRealName(row.nickName());
        request.setEmployeeNo(row.employeeNo());
        request.setPositionName(row.positionName());
        request.setPhone(row.phone());
        request.setEmail(row.email());
        request.setDeptId(deptByName.get(row.deptName().trim()).getDeptId());
        request.setEnable(parseEnable(row.enable()));
        request.setRoleIds(UserQuerySupport.splitBusinessCodes(row.roleCodes()).stream()
                .map(roleCode -> String.valueOf(roleByCode.get(roleCode).getRoleId()))
                .collect(Collectors.joining(",")));
        return request;
    }

    private Integer parseEnable(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }
        String normalized = value.trim();
        if ("1".equals(normalized) || "启用".equals(normalized)) {
            return 1;
        }
        if ("0".equals(normalized) || "停用".equals(normalized) || "禁用".equals(normalized)) {
            return 0;
        }
        return null;
    }

    private void createImportedUser(UserRequest request) {
        transactionTemplate.executeWithoutResult(status -> {
            UserRequestNormalizer.normalize(request);
            request.setPassword(passwordHasher.hash(easyNextAdminConfig.getDefaultPwd()));
            UserBO userBO = userBeanMap.requestToBo(request);
            SysUser sysUser = userBeanMap.boToEntity(userBO);
            int inserted = sysUserMapper.insert(sysUser);
            if (inserted != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户导入失败");
            }
            saveUserRoles(sysUser.getUserId(), UserQuerySupport.splitRoleIds(request.getRoleIds()));
        });
    }

    private void saveUserRoles(Long userId, List<String> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        List<SysUserRole> userRoles = roleIds.stream()
                .map(roleId -> {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setRoleId(Long.valueOf(roleId));
                    userRole.setUserId(userId);
                    return userRole;
                })
                .toList();
        if (!sysUserRoleService.saveBatch(userRoles)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户角色绑定失败");
        }
    }

    private UserCsvCodec.ExportRow toExportRow(SysUser user, Map<Long, SysDept> deptById, Map<Long, SysRole> roleById) {
        SysDept dept = deptById.get(user.getDeptId());
        List<SysRole> roles = UserQuerySupport.splitRoleIds(user.getRoleIds()).stream()
                .map(Long::valueOf)
                .map(roleById::get)
                .filter(Objects::nonNull)
                .toList();
        return new UserCsvCodec.ExportRow(
                user.getUserName(),
                user.getNickName(),
                user.getEmployeeNo(),
                user.getPositionName(),
                user.getPhone(),
                user.getEmail(),
                dept == null ? user.getDeptName() : dept.getDeptName(),
                roles.stream().map(SysRole::getRoleCode).collect(Collectors.joining("|")),
                roles.stream().map(SysRole::getRoleName).collect(Collectors.joining("|")),
                user.getEnable()
        );
    }

    private String importErrorMessage(RuntimeException ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "用户导入失败";
    }
}
