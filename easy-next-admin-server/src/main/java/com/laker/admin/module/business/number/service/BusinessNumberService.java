package com.laker.admin.module.business.number.service;

public interface BusinessNumberService {

    /**
     * 按规则编码生成下一个业务编号。
     *
     * @param ruleCode 业务编号规则编码，例如 PURCHASE_REQUEST
     * @return 用户可见的业务编号，例如 PR-20260625-000001
     */
    String nextNumber(String ruleCode);
}
