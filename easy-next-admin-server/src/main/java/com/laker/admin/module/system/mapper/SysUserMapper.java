package com.laker.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeColumns;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScopeMapperMethods;
import com.laker.admin.module.system.dto.workbench.EnabledCountSummary;
import com.laker.admin.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author laker
 * @since 2021-08-05
 */
@DataScope(
        methods = {DataScopeMapperMethods.SELECT_LIST, DataScopeMapperMethods.SELECT_PAGE},
        deptColumn = DataScopeColumns.DB_DEPT_ID,
        selfColumn = DataScopeColumns.USER_ID
)
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            select count(*) as total,
                   coalesce(sum(case when enable = 1 then 1 else 0 end), 0) as enabled_total
              from sys_user
             where deleted = 0
            """)
    EnabledCountSummary selectEnabledCountSummary();
}
