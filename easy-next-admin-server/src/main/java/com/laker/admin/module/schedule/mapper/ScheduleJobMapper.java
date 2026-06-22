package com.laker.admin.module.schedule.mapper;

import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeColumns;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeMapperMethods;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author easynext
 * @since 2021-08-17
 */
@DataScope(
        methods = {DataScopeMapperMethods.SELECT_LIST, DataScopeMapperMethods.SELECT_PAGE},
        deptColumn = DataScopeColumns.DB_CREATE_DEPT_ID,
        selfColumn = DataScopeColumns.DB_CREATE_BY
)
public interface ScheduleJobMapper extends BaseMapper<ScheduleJob> {

}
