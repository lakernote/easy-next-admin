package com.laker.admin.module.system.mapper;

import com.laker.admin.module.system.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.system.dto.workbench.EnabledCountSummary;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author laker
 * @since 2021-08-11
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            select count(*) as total,
                   coalesce(sum(case when enable = 1 then 1 else 0 end), 0) as enabled_total
              from sys_role
             where deleted = 0
            """)
    EnabledCountSummary selectEnabledCountSummary();
}
