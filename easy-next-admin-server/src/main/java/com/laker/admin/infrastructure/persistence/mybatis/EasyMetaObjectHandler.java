package com.laker.admin.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.laker.admin.common.util.EasyNextAdminSecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author laker
 */
@Slf4j
@Component
public class EasyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("start insert fill ....");
        UserInfoAndPermissions userInfoAndPowers = EasyNextAdminSecurityUtils.getCurrentUserInfo();
        Long userId = currentUserId(userInfoAndPowers);
        Long deptId = userInfoAndPowers == null ? null : userInfoAndPowers.getDeptId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
        if (deptId != null) {
            this.strictInsertFill(metaObject, "createDeptId", Long.class, deptId);
        }
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "version", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("start update fill ....");
        Long userId = currentUserId(EasyNextAdminSecurityUtils.getCurrentUserInfo());
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
    }

    private Long currentUserId(UserInfoAndPermissions userInfoAndPowers) {
        if (userInfoAndPowers != null) {
            return userInfoAndPowers.getUserId();
        }
        return null;
    }
}
