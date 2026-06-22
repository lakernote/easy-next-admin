package com.laker.admin.infrastructure.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.mapper.SysUserMapper;
import com.laker.admin.module.system.service.ISysUserRoleService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.util.CollectionUtils;

/**
 * 权限版本服务。
 *
 * <p>用户角色绑定、角色权限、菜单资源变化后递增用户权限版本。
 * 在线会话中的 LoginUser 快照会携带版本号，请求进来发现版本不一致时立即失效，保证授权变更及时生效。</p>
 */
@Service
public class PermissionVersionService {
    private final SysUserMapper sysUserMapper;
    private final ISysUserRoleService sysUserRoleService;

    public PermissionVersionService(SysUserMapper sysUserMapper, ISysUserRoleService sysUserRoleService) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleService = sysUserRoleService;
    }

    public void increaseForUser(Long userId) {
        if (userId == null) {
            return;
        }
        increaseForUsers(List.of(userId));
    }

    public void increaseForUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .setSql("permission_version = permission_version + 1")
                .in(SysUser::getUserId, ids));
    }

    public void increaseForRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        increaseForUsers(sysUserRoleService.listUserIdsByRoleId(roleId));
    }

    public void increaseForAllUsers() {
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .setSql("permission_version = permission_version + 1"));
    }
}
