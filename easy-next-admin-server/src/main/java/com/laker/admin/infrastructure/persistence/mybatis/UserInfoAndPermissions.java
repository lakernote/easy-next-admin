package com.laker.admin.infrastructure.persistence.mybatis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * 当前用户的持久化层上下文快照。
 *
 * <p>这里只给 MyBatis 自动填充和数据权限读取基础身份信息，接口是否允许访问仍由 @EasyPermission 判断。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoAndPermissions {
    private Long userId;
    private String userName;
    private String nickName;
    private Long deptId;
    private String deptName;
    private Map<String, Object> metaData;
    private String deptTableAlias;
    private String userTableAlias;
    private String sql;
    private Set<Long> deptIds;

    public Boolean isSuperAdmin() {
        return userId == 1L;
    }

}
