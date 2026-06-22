package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.system.entity.SysDept;
import com.laker.admin.module.system.entity.SysUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SysDeptServiceImplTest {

    private final SysDeptServiceImpl service = new SysDeptServiceImpl(mock(SysUserMapper.class));

    @Test
    void shouldNormalizeDepartmentBusinessFieldsBeforeSave() {
        SysDept department = new SysDept();
        department.setDeptName(" 客户运营组 ");
        department.setFullName("   ");
        department.setAddress(" 广州 ");
        department.setRemark("   ");

        service.normalizeDepartment(department);

        assertThat(department.getDeptName()).isEqualTo("客户运营组");
        assertThat(department.getFullName()).isNull();
        assertThat(department.getAddress()).isEqualTo("广州");
        assertThat(department.getRemark()).isNull();
        assertThat(department.getPid()).isZero();
        assertThat(department.getStatus()).isTrue();
        assertThat(department.getSort()).isEqualTo(99);
    }

    @Test
    void shouldAllowClearingWorkflowRelationshipFields() throws NoSuchFieldException {
        TableField departmentLeaderField = SysDept.class.getDeclaredField("leaderUserId").getAnnotation(TableField.class);
        TableField userManagerField = SysUser.class.getDeclaredField("managerUserId").getAnnotation(TableField.class);

        assertThat(departmentLeaderField.updateStrategy()).isEqualTo(FieldStrategy.ALWAYS);
        assertThat(userManagerField.updateStrategy()).isEqualTo(FieldStrategy.ALWAYS);
    }
}
