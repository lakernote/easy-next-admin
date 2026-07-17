package com.laker.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.infrastructure.observability.trace.EasyTrace;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeColumns;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeMapperMethods;
import com.laker.admin.module.system.entity.SysDept;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author laker
 * @since 2021-08-11
 */
@DataScope(
        methods = {DataScopeMapperMethods.SELECT_LIST, DataScopeMapperMethods.SELECT_PAGE},
        selfColumn = DataScopeColumns.DEPT_ID
)
@EasyTrace(spanType = SpanType.Mapper)
public interface SysDeptMapper extends BaseMapper<SysDept> {

}
