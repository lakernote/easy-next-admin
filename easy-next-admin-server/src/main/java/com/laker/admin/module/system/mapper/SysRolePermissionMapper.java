package com.laker.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.system.entity.SysRolePermission;
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
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Select("""
            select rp.permission_resource_id
              from sys_role_permission rp
             where rp.deleted = 0
               and rp.role_id = #{roleId}
             order by rp.permission_resource_id
            """)
    List<Long> selectPermissionResourceIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            select distinct m.permission_code
              from sys_role_permission rp
              join sys_menu m
                on m.id = rp.permission_resource_id
               and m.deleted = 0
             where rp.deleted = 0
               and rp.role_id = #{roleId}
               and m.permission_code is not null
               and m.permission_code <> ''
             order by m.permission_code
            """)
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);

    @Delete("delete from sys_role_permission where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
