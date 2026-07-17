package com.laker.admin.module.audit.dto;

import com.laker.admin.module.system.entity.SysUser;
import lombok.Builder;
import lombok.Data;

/**
 * 审计列表里展示的操作人摘要。
 */
@Data
@Builder
public class AuditUserView {
    private String userName;
    private String nickName;
    private String realName;

    public static AuditUserView from(SysUser user) {
        if (user == null) {
            return null;
        }
        return AuditUserView.builder()
                .userName(user.getUserName())
                .nickName(user.getNickName())
                .realName(user.getRealName())
                .build();
    }
}
