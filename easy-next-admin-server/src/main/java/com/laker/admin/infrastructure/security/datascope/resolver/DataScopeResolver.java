package com.laker.admin.infrastructure.security.datascope.resolver;

import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;

public interface DataScopeResolver {
    /**
     * 解析当前登录账号在本次请求中的数据可见范围。
     */
    DataScopeCondition resolveCurrentUserScope();
}
