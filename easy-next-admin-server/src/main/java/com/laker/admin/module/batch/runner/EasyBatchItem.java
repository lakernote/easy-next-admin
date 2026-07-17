package com.laker.admin.module.batch.runner;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

public record EasyBatchItem<T>(String itemKey, String itemName, T data, String inputJson, String remark) {

    public EasyBatchItem {
        if (!StringUtils.hasText(itemKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理明细业务键不能为空");
        }
        itemKey = itemKey.trim();
        itemName = StringUtils.hasText(itemName) ? itemName.trim() : itemKey;
        inputJson = StringUtils.hasText(inputJson) ? inputJson.trim() : null;
        remark = StringUtils.hasText(remark) ? remark.trim() : null;
    }

    public static <T> EasyBatchItem<T> of(String itemKey, String itemName, T data) {
        return new EasyBatchItem<>(itemKey, itemName, data, null, null);
    }
}
