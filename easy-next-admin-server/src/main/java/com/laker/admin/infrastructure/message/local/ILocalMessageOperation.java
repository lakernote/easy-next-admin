package com.laker.admin.infrastructure.message.local;

import java.util.Map;

public interface ILocalMessageOperation {
    /**
     * 本地操作
     *
     * @param params 参数
     * @return 是否成功
     */
    boolean localOperation(Map<String, Object> params);

    /**
     * 远程操作
     *
     * @param params 参数
     * @return 是否成功
     */
    boolean remoteOperation(Map<String, Object> params);
}
