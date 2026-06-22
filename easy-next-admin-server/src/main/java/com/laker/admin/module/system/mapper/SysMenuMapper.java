package com.laker.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.system.dto.workbench.PermissionResourceSummary;
import com.laker.admin.module.system.entity.SysPower;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统菜单权限资源 Mapper。
 *
 * @author laker
 * @since 2021-08-04
 */
public interface SysMenuMapper extends BaseMapper<SysPower> {

    @Select("""
            select coalesce(sum(case when type in (0, 1) then 1 else 0 end), 0) as menu_total,
                   coalesce(sum(case when type = 2 then 1 else 0 end), 0) as action_total
              from sys_menu
             where deleted = 0
            """)
    PermissionResourceSummary selectPermissionResourceSummary();

    @Select("""
            select id as menuId,
                   pid,
                   title,
                   icon,
                   href,
                   sort,
                   enable,
                   remark,
                   create_time as createTime,
                   update_by as updateBy,
                   update_time as updateTime,
                   deleted,
                   version,
                   type,
                   power_code as powerCode,
                   component_path as componentPath,
                   visible,
                   create_by as createBy,
                   create_dept_id as createDeptId
              from sys_menu
             where enable = #{enable}
               and deleted = 0
             order by pid, sort, id
            """)
    List<SysPower> findAllByStatusOrderBySort(Boolean enable);

    @Select("""
            select distinct
                   m.id as menuId,
                   m.pid,
                   m.title,
                   m.icon,
                   m.href,
                   m.sort,
                   m.enable,
                   m.remark,
                   m.create_time as createTime,
                   m.update_by as updateBy,
                   m.update_time as updateTime,
                   m.deleted,
                   m.version,
                   m.type,
                   m.power_code as powerCode,
                   m.component_path as componentPath,
                   m.visible,
                   m.create_by as createBy,
                   m.create_dept_id as createDeptId
              from sys_user_role ur
              join sys_role r
                on r.id = ur.role_id
               and r.deleted = 0
               and r.enable = 1
              join sys_role_permission rp
                on rp.role_id = ur.role_id
               and rp.deleted = 0
              join sys_menu m
                on m.id = rp.power_id
               and m.deleted = 0
               and m.enable = 1
             where ur.deleted = 0
               and ur.user_id = #{userId}
             order by m.pid, m.sort, m.id
            """)
    List<SysPower> findEnabledByUserId(@Param("userId") Long userId);
}
