package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.module.system.dto.SystemUserQuery;
import com.laker.admin.module.system.dto.SystemUserView;
import com.laker.admin.module.system.dto.user.UserImportResult;
import com.laker.admin.module.system.dto.user.UserRequest;
import com.laker.admin.module.system.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysUserServiceIntegrationTest {
    private static final long CURRENT_DEPT_USER = 8201L;
    private static final long OTHER_DEPT_USER = 8202L;
    private static final long CURRENT_DEPT = 202604280103000001L;
    private static final long OTHER_DEPT = 202604280103000002L;

    @Autowired
    private ISysUserService sysUserService;

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void workflowAssigneesShouldRespectCurrentUsersDataScope() {
        sysUserService.save(activeUser(CURRENT_DEPT_USER, "current_dept_user", "当前部门用户", CURRENT_DEPT));
        sysUserService.save(activeUser(OTHER_DEPT_USER, "finance_user", "财务复核人", OTHER_DEPT));
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(CURRENT_DEPT_USER)
                .userName("current_dept_user")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.DEPT))
                .superAdmin(false)
                .build());

        assertThat(sysUserService.listWorkflowAssignees())
                .extracting(item -> item.getValue())
                .contains(String.valueOf(CURRENT_DEPT_USER))
                .doesNotContain(String.valueOf(OTHER_DEPT_USER));
    }

    @Test
    void workflowAssigneesShouldIncludeCurrentUsersManagerWhenEditingUser() {
        SysUser currentDeptUser = activeUser(CURRENT_DEPT_USER, "current_dept_user", "当前部门用户", CURRENT_DEPT);
        currentDeptUser.setManagerUserId(OTHER_DEPT_USER);
        sysUserService.save(currentDeptUser);
        sysUserService.save(activeUser(OTHER_DEPT_USER, "finance_manager", "财务负责人", OTHER_DEPT));
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(CURRENT_DEPT_USER)
                .userName("current_dept_user")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.DEPT))
                .superAdmin(false)
                .build());

        assertThat(sysUserService.listWorkflowAssignees())
                .extracting(item -> item.getValue())
                .doesNotContain(String.valueOf(OTHER_DEPT_USER));
        assertThat(sysUserService.listWorkflowAssignees(CURRENT_DEPT_USER))
                .extracting(item -> item.getValue())
                .contains(String.valueOf(CURRENT_DEPT_USER), String.valueOf(OTHER_DEPT_USER));
    }

    @Test
    void shouldImportUsersFromCsvAndExportUserCsv() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                ("\uFEFF用户名,姓名,员工编号,岗位,手机号,邮箱,部门名称,角色编码,启用\n"
                        + "import_staff,导入员工,EA900001,客户运营专员,13900009001,import.staff@example.com,易企科技有限公司,admin,1\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        UserImportResult result = sysUserService.importUsers(file);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).isZero();
        SysUser imported = sysUserService.getOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUserName, "import_staff"));
        assertThat(imported).isNotNull();
        assertThat(imported.getNickName()).isEqualTo("导入员工");
        assertThat(imported.getDeptId()).isEqualTo(202604280103000001L);

        String exported = new String(sysUserService.exportUsers(new SystemUserQuery()), StandardCharsets.UTF_8);

        assertThat(exported).contains("用户名,姓名,员工编号,岗位,手机号,邮箱,部门名称,角色编码,角色名称,启用");
        assertThat(exported).contains("import_staff", "导入员工", "EA900001", "易企科技有限公司", "admin");
    }

    @Test
    void shouldAcceptCsvImportContentTypeWithCharset() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv;charset=UTF-8",
                ("用户名,姓名,员工编号,岗位,手机号,邮箱,部门名称,角色编码,启用\n"
                        + "import_charset_staff,导入员工,EA900002,客户运营专员,13900009002,import.charset@example.com,易企科技有限公司,admin,1\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        UserImportResult result = sysUserService.importUsers(file);

        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).isZero();
    }

    @Test
    void shouldCreateMultipleUsersWithoutEmployeeNo() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());

        SystemUserView first = sysUserService.createUser(blankEmployeeUser("blank_employee_1", "空员工编号一"));
        SystemUserView second = sysUserService.createUser(blankEmployeeUser("blank_employee_2", "空员工编号二"));

        assertThat(first.getUserId()).isNotNull();
        assertThat(second.getUserId()).isNotNull();
        assertThat(sysUserService.getById(first.getUserId()).getEmployeeNo()).isNull();
        assertThat(sysUserService.getById(second.getUserId()).getEmployeeNo()).isNull();
    }

    @Test
    void shouldClearManagerUserWhenUpdateWithNullManager() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());
        UserRequest createRequest = blankEmployeeUser("clear_manager_user", "清空直属上级用户");
        createRequest.setManagerUserId(202604280101000001L);
        SystemUserView created = sysUserService.createUser(createRequest);

        UserRequest updateRequest = blankEmployeeUser("clear_manager_user", "清空直属上级用户");
        updateRequest.setUserId(created.getUserId());
        updateRequest.setManagerUserId(null);
        sysUserService.updateUser(created.getUserId(), updateRequest);

        assertThat(sysUserService.getById(created.getUserId()).getManagerUserId()).isNull();
    }

    @Test
    void shouldRejectEnglishImportHeaders() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                ("username,name,deptName,roleCode\n"
                        + "english_staff,英文表头用户,易企科技有限公司,admin\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> sysUserService.importUsers(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件缺少必要列：用户名");
    }

    @Test
    void shouldRejectImportFileOverSizeLimit() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                new byte[2 * 1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> sysUserService.importUsers(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不能超过 2MB");
    }

    @Test
    void exportShouldEscapeCsvFormulaCells() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.ALL))
                .superAdmin(true)
                .build());
        sysUserService.save(activeUser(8301L, "formula_user", "=cmd", CURRENT_DEPT));

        String exported = new String(sysUserService.exportUsers(new SystemUserQuery()), StandardCharsets.UTF_8);

        assertThat(exported).contains("'=cmd");
    }

    @Test
    void userDetailShouldRejectOutOfScopeUser() {
        sysUserService.save(activeUser(CURRENT_DEPT_USER, "current_detail_user", "当前部门用户", CURRENT_DEPT));
        sysUserService.save(activeUser(OTHER_DEPT_USER, "other_detail_user", "其他部门用户", OTHER_DEPT));
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(CURRENT_DEPT_USER)
                .userName("current_detail_user")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.DEPT))
                .superAdmin(false)
                .build());

        assertThatThrownBy(() -> sysUserService.getUserAndDeptById(OTHER_DEPT_USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看权限范围外的用户");
    }

    private SysUser activeUser(long userId, String userName, String nickName, long deptId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setDeptId(deptId);
        user.setEnable(1);
        user.setDeleted(0);
        user.setVersion(0);
        user.setPermissionVersion(1L);
        user.setEmployeeNo("IT" + userId);
        return user;
    }

    private UserRequest blankEmployeeUser(String userName, String nickName) {
        UserRequest request = new UserRequest();
        request.setUserName(userName);
        request.setNickName(nickName);
        request.setRealName("  ");
        request.setEmployeeNo("  ");
        request.setPositionName("");
        request.setPhone("");
        request.setEmail(" ");
        request.setDeptId(CURRENT_DEPT);
        request.setEnable(1);
        return request;
    }

}
