package com.laker.admin.infrastructure.id;

import java.util.UUID;

/**
 * 平台内部标识生成入口。
 *
 * <p>业务表主键继续交给 MyBatis-Plus 雪花 ID；这里用于 traceId、锁 token、文件名等非业务主键场景。
 * 统一入口后，后续如果要切换成 ULID、NanoId 或带业务前缀的编号，不需要改业务代码。</p>
 */
public final class EasyIdGenerator {

    private EasyIdGenerator() {
    }

    public static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
