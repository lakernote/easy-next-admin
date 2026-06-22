package com.laker.admin.module.system.controller;

import com.laker.admin.module.system.dto.SystemDepartmentView;
import com.laker.admin.module.system.entity.SysDept;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SystemDepartmentContractTest {

    @Test
    void departmentControllerShouldUseDtoContractInsteadOfPersistenceEntity() {
        Method treeMethod = declaredMethod("tree");
        Method saveMethod = declaredMethod("saveOrUpdate");
        Method getMethod = declaredMethod("get");

        assertThat(treeMethod.getGenericReturnType().getTypeName())
                .contains("SystemDepartmentView")
                .doesNotContain("SysDept");
        assertThat(getMethod.getGenericReturnType().getTypeName())
                .contains("SystemDepartmentView")
                .doesNotContain("SysDept");
        assertThat(saveMethod.getParameterTypes())
                .extracting(Class::getSimpleName)
                .containsExactly("SystemDepartmentRequest");
        assertThat(saveMethod.getParameterTypes())
                .doesNotContain(SysDept.class);
    }

    @Test
    void departmentViewShouldExposeOnlyEnterpriseOrganizationFields() {
        Set<String> fields = Arrays.stream(SystemDepartmentView.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        assertThat(fields)
                .contains("deptId", "deptName", "fullName", "address", "pid", "leaderUserId", "status", "sort")
                .doesNotContain("treePath", "createBy", "createDeptId", "updateBy", "deleted", "version", "remark");
    }

    private static Method declaredMethod(String name) {
        return Arrays.stream(SysDeptController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
