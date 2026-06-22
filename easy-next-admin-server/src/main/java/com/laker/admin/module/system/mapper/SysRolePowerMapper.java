package com.laker.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.system.entity.SysRolePower;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限关系 Mapper。
 *
 * @author laker
 * @since 2021-08-11
 */
public interface SysRolePowerMapper extends BaseMapper<SysRolePower> {

    @Select("""
            select rp.power_id
              from sys_role_permission rp
             where rp.deleted = 0
               and rp.role_id = #{roleId}
             order by rp.power_id
            """)
    List<Long> selectPowerIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            select distinct m.power_code
              from sys_role_permission rp
              join sys_menu m
                on m.id = rp.power_id
               and m.deleted = 0
             where rp.deleted = 0
               and rp.role_id = #{roleId}
               and m.power_code is not null
               and m.power_code <> ''
             order by m.power_code
            """)
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);

    @Delete("delete from sys_role_permission where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
