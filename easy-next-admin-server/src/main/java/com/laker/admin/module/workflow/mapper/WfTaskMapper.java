package com.laker.admin.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeColumns;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeMapperMethods;
import com.laker.admin.module.workflow.entity.WfTask;

@DataScope(
        methods = {DataScopeMapperMethods.SELECT_LIST, DataScopeMapperMethods.SELECT_PAGE},
        deptColumn = DataScopeColumns.DB_ASSIGNEE_DEPT_ID,
        selfColumn = DataScopeColumns.DB_ASSIGNEE_ID
)
public interface WfTaskMapper extends BaseMapper<WfTask> {
}
