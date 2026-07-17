package com.laker.admin.module.system.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.laker.admin.module.system.dto.SystemUserQuery;
import com.laker.admin.module.system.entity.SysUser;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

public final class UserQuerySupport {

    private UserQuerySupport() {
    }

    public static LambdaQueryWrapper<SysUser> buildUserQuery(SystemUserQuery query) {
        SystemUserQuery actualQuery = query == null ? new SystemUserQuery() : query;
        LambdaQueryWrapper<SysUser> queryWrapper = new QueryWrapper<SysUser>().lambda();
        queryWrapper.eq(actualQuery.getDeptId() != null, SysUser::getDeptId, actualQuery.getDeptId())
                .eq(actualQuery.getEnable() != null, SysUser::getEnable, actualQuery.getEnable())
                .and(StringUtils.hasText(actualQuery.getKeyWord()), wrapper -> wrapper
                        .like(SysUser::getUserName, actualQuery.getKeyWord())
                        .or().like(SysUser::getNickName, actualQuery.getKeyWord())
                        .or().like(SysUser::getRealName, actualQuery.getKeyWord())
                        .or().like(SysUser::getEmployeeNo, actualQuery.getKeyWord())
                        .or().like(SysUser::getPositionName, actualQuery.getKeyWord())
                        .or().like(SysUser::getPhone, actualQuery.getKeyWord())
                        .or().like(SysUser::getEmail, actualQuery.getKeyWord()));
        return queryWrapper;
    }

    public static List<String> splitRoleIds(String roleIds) {
        if (!StringUtils.hasText(roleIds)) {
            return List.of();
        }
        return Arrays.stream(roleIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    public static List<String> splitBusinessCodes(String codes) {
        if (!StringUtils.hasText(codes)) {
            return List.of();
        }
        return Arrays.stream(codes.split("[|;；、\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
