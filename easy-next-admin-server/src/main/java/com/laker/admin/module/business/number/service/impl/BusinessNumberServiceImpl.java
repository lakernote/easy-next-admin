package com.laker.admin.module.business.number.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.module.business.number.entity.BusinessNumberRule;
import com.laker.admin.module.business.number.entity.BusinessNumberSequence;
import com.laker.admin.module.business.number.mapper.BusinessNumberRuleMapper;
import com.laker.admin.module.business.number.mapper.BusinessNumberSequenceMapper;
import com.laker.admin.module.business.number.service.BusinessNumberService;
import com.laker.admin.module.business.number.support.BusinessNumberFormatter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class BusinessNumberServiceImpl implements BusinessNumberService {
    private final BusinessNumberRuleMapper ruleMapper;
    private final BusinessNumberSequenceMapper sequenceMapper;

    public BusinessNumberServiceImpl(BusinessNumberRuleMapper ruleMapper,
                                     BusinessNumberSequenceMapper sequenceMapper) {
        this.ruleMapper = ruleMapper;
        this.sequenceMapper = sequenceMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String nextNumber(String ruleCode) {
        String normalizedRuleCode = normalizeRuleCode(ruleCode);
        BusinessNumberRule rule = ruleMapper.selectOne(Wrappers.<BusinessNumberRule>lambdaQuery()
                .eq(BusinessNumberRule::getRuleCode, normalizedRuleCode));
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "业务编号规则不存在：" + normalizedRuleCode);
        }
        if (!Boolean.TRUE.equals(rule.getEnable())) {
            throw new BusinessException("业务编号规则已停用：" + normalizedRuleCode);
        }
        LocalDate today = LocalDate.now();
        String segment = BusinessNumberFormatter.segment(rule, today);
        String sequenceKey = BusinessNumberFormatter.sequenceKey(rule.getRuleCode(), segment);
        ensureSequenceRow(rule, segment, sequenceKey);
        BusinessNumberSequence sequence = sequenceMapper.selectForUpdate(sequenceKey);
        if (sequence == null) {
            throw new BusinessException("业务编号计数器不存在：" + sequenceKey);
        }
        long nextValue = Math.addExact(sequence.getCurrentValue(), rule.getSequenceStep());
        sequence.setCurrentValue(nextValue);
        sequence.setUpdatedAt(LocalDateTime.now());
        sequenceMapper.updateById(sequence);
        return BusinessNumberFormatter.format(rule, today, nextValue);
    }

    private void ensureSequenceRow(BusinessNumberRule rule, String segment, String sequenceKey) {
        if (sequenceMapper.selectById(sequenceKey) != null) {
            return;
        }
        BusinessNumberSequence sequence = new BusinessNumberSequence();
        sequence.setSequenceKey(sequenceKey);
        sequence.setRuleCode(rule.getRuleCode());
        sequence.setSegment(segment);
        sequence.setCurrentValue(rule.getInitialValue());
        sequence.setUpdatedAt(LocalDateTime.now());
        try {
            sequenceMapper.insert(sequence);
        } catch (DuplicateKeyException ignored) {
            // 并发请求可能已经先创建了当前周期的计数器。
        }
    }

    private String normalizeRuleCode(String ruleCode) {
        if (!StringUtils.hasText(ruleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "业务编号规则编码不能为空");
        }
        return ruleCode.trim().toUpperCase();
    }
}
