package com.laker.admin.module.schedule.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum JobStateEnum {
    START(1, "开始"), STOP(0, "暂停");

    JobStateEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @EnumValue//标记数据库存的值是code
    private final int code;
    private final String description;
}
