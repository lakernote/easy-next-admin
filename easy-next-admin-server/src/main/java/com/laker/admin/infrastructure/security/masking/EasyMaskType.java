package com.laker.admin.infrastructure.security.masking;

/**
 * 常见企业后台敏感字段类型，供 DTO 字段注解和审计脱敏组件共用。
 */
public enum EasyMaskType {
    FULL,
    PHONE,
    EMAIL,
    NAME,
    ID_CARD,
    BANK_CARD,
    CUSTOM
}
