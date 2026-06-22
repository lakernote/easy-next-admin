package com.laker.admin.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.system.dto.RoleUserCount;
import com.laker.admin.module.system.dto.UserRoleBinding;
import com.laker.admin.module.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 用户角色关系 Mapper。
 *
 * @author laker
 * @since 2021-08-11
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
            <script>
            select ur.user_id as userId,
                   ur.role_id as roleId,
                   r.role_code as roleCode,
                   r.role_name as roleName
              from sys_user_role ur
              join sys_role r
                on r.id = ur.role_id
               and r.deleted = 0
             where ur.deleted = 0
               and ur.user_id in
             <foreach collection="userIds" item="userId" open="(" separator="," close=")">
               #{userId}
             </foreach>
             order by ur.user_id, ur.role_id
            </script>
            """)
    List<UserRoleBinding> selectRoleBindingsByUserIds(@Param("userIds") Collection<Long> userIds);

    @Select("""
            <script>
            select ur.role_id as roleId,
                   count(distinct ur.user_id) as userCount
              from sys_user_role ur
              join sys_user u
                on u.id = ur.user_id
               and u.deleted = 0
             where ur.deleted = 0
               and ur.role_id in
             <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
               #{roleId}
             </foreach>
             group by ur.role_id
            </script>
            """)
    List<RoleUserCount> countUsersByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    @Select("""
            select role_id
              from sys_user_role
             where deleted = 0
               and user_id = #{userId}
             order by role_id
            """)
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Select("""
            select distinct user_id
              from sys_user_role
             where deleted = 0
               and role_id = #{roleId}
             order by user_id
            """)
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            <script>
            select distinct user_id
              from sys_user_role
             where deleted = 0
               and role_id in
             <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
               #{roleId}
             </foreach>
             order by user_id
            </script>
            """)
    List<Long> selectUserIdsByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    @Delete("delete from sys_user_role where user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Delete("""
            <script>
            delete from sys_user_role
             where user_id in
             <foreach collection="userIds" item="userId" open="(" separator="," close=")">
               #{userId}
             </foreach>
            </script>
            """)
    int deleteByUserIds(@Param("userIds") Collection<Long> userIds);

    @Delete("delete from sys_user_role where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
