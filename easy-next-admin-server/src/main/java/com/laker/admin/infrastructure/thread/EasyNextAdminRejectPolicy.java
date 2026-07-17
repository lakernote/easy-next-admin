package com.laker.admin.infrastructure.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;

/**
 * 自定义扩展拒绝策略
 *
 * @author easynext
 *
 *
 */
@Slf4j
public class EasyNextAdminRejectPolicy extends AbortPolicy {

    /** {@inheritDoc} */
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        log.warn("触发线程拒绝策略：\r\n{}", "Task " + r.toString() + " rejected from "
                + e.toString());
        super.rejectedExecution(r, e);
    }

}
