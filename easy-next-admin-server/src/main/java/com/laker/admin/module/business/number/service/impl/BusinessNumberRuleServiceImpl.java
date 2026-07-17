package com.laker.admin.module.business.number.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleQuery;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleRequest;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleView;
import com.laker.admin.module.business.number.entity.BusinessNumberRule;
import com.laker.admin.module.business.number.mapper.BusinessNumberRuleMapper;
import com.laker.admin.module.business.number.service.IBusinessNumberRuleService;
import com.laker.admin.module.business.number.support.BusinessNumberFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Service
public class BusinessNumberRuleServiceImpl extends ServiceImpl<BusinessNumberRuleMapper, BusinessNumberRule>
        implements IBusinessNumberRuleService {
    private static final Pattern RULE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[A-Z][A-Z0-9]{0,15}");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[-_/]{0,4}");
    private static final int DEFAULT_SEQUENCE_WIDTH = 6;
    private static final int DEFAULT_SEQUENCE_STEP = 1;
    private static final long DEFAULT_INITIAL_VALUE = 0L;

    @Override
    public PageResponse<BusinessNumberRuleView> pageRules(BusinessNumberRuleQuery query) {
        BusinessNumberRuleQuery actualQuery = query == null ? new BusinessNumberRuleQuery() : query;
        Page<BusinessNumberRule> page = EasyPageSupport.page(actualQuery.getPage(), actualQuery.getLimit());
        LambdaQueryWrapper<BusinessNumberRule> wrapper = new QueryWrapper<BusinessNumberRule>().lambda();
        wrapper.eq(actualQuery.getEnable() != null, BusinessNumberRule::getEnable, actualQuery.getEnable())
                .and(StringUtils.hasText(actualQuery.getKeyword()), item -> item
                        .like(BusinessNumberRule::getRuleCode, actualQuery.getKeyword())
                        .or()
                        .like(BusinessNumberRule::getRuleName, actualQuery.getKeyword())
                        .or()
                        .like(BusinessNumberRule::getPrefix, actualQuery.getKeyword()))
                .orderByDesc(BusinessNumberRule::getUpdateTime);
        Page<BusinessNumberRule> result = this.page(page, wrapper);
        return PageResponse.ok(result.getRecords().stream().map(BusinessNumberRuleView::from).toList(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessNumberRuleView saveRule(BusinessNumberRuleRequest request) {
        validateRuleRequest(request);
        BusinessNumberRule rule = request.getId() == null ? new BusinessNumberRule() : requireRule(request.getId());
        if (rule.getId() != null && !rule.getRuleCode().equals(normalizeCode(request.getRuleCode()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "规则编码创建后不能修改");
        }
        boolean exists = lambdaQuery()
                .eq(BusinessNumberRule::getRuleCode, normalizeCode(request.getRuleCode()))
                .ne(request.getId() != null, BusinessNumberRule::getId, request.getId())
                .exists();
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "业务编号规则编码已存在");
        }
        fillRule(rule, request);
        boolean saved = saveOrUpdate(rule);
        if (!saved) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "业务编号规则保存失败");
        }
        return BusinessNumberRuleView.from(getById(rule.getId()));
    }

    @Override
    public BusinessNumberRuleView getRule(Long id) {
        return BusinessNumberRuleView.from(requireRule(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRule(Long id) {
        requireRule(id);
        return removeById(id);
    }

    private void validateRuleRequest(BusinessNumberRuleRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "业务编号规则不能为空");
        }
        String ruleCode = normalizeCode(request.getRuleCode());
        if (!RULE_CODE_PATTERN.matcher(ruleCode).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "规则编码只能使用大写字母、数字和下划线，并以字母开头");
        }
        if (!StringUtils.hasText(request.getRuleName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "规则名称不能为空");
        }
        String prefix = normalizeCode(request.getPrefix());
        if (!PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号前缀只能使用大写字母和数字，并以字母开头");
        }
        BusinessNumberFormatter.normalizeDatePattern(request.getDatePattern());
        String separator = BusinessNumberFormatter.normalizeSeparator(request.getSeparator());
        if (!SEPARATOR_PATTERN.matcher(separator).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分隔符只能使用 -、_、/ 或留空");
        }
        int sequenceWidth = request.getSequenceWidth() == null ? DEFAULT_SEQUENCE_WIDTH : request.getSequenceWidth();
        int sequenceStep = request.getSequenceStep() == null ? DEFAULT_SEQUENCE_STEP : request.getSequenceStep();
        long initialValue = request.getInitialValue() == null ? DEFAULT_INITIAL_VALUE : request.getInitialValue();
        if (sequenceWidth < 1 || sequenceWidth > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "流水位数必须在 1 到 12 之间");
        }
        if (sequenceStep < 1 || sequenceStep > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "递增步长必须在 1 到 1000 之间");
        }
        if (initialValue < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "初始当前值不能小于 0");
        }
        BusinessNumberRule previewRule = new BusinessNumberRule();
        previewRule.setPrefix(prefix);
        previewRule.setDatePattern(BusinessNumberFormatter.normalizeDatePattern(request.getDatePattern()));
        previewRule.setNumberSeparator(separator);
        previewRule.setSequenceWidth(sequenceWidth);
        String preview = BusinessNumberFormatter.format(previewRule, LocalDate.now(), initialValue + sequenceStep);
        if (preview.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号长度不能超过 64 个字符");
        }
    }

    private void fillRule(BusinessNumberRule rule, BusinessNumberRuleRequest request) {
        rule.setRuleCode(normalizeCode(request.getRuleCode()));
        rule.setRuleName(request.getRuleName().trim());
        rule.setPrefix(normalizeCode(request.getPrefix()));
        rule.setDatePattern(BusinessNumberFormatter.normalizeDatePattern(request.getDatePattern()));
        rule.setNumberSeparator(BusinessNumberFormatter.normalizeSeparator(request.getSeparator()));
        rule.setSequenceWidth(request.getSequenceWidth() == null ? DEFAULT_SEQUENCE_WIDTH : request.getSequenceWidth());
        rule.setSequenceStep(request.getSequenceStep() == null ? DEFAULT_SEQUENCE_STEP : request.getSequenceStep());
        rule.setInitialValue(request.getInitialValue() == null ? DEFAULT_INITIAL_VALUE : request.getInitialValue());
        rule.setEnable(request.getEnable() == null || request.getEnable());
        rule.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
    }

    private BusinessNumberRule requireRule(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "业务编号规则 ID 不能为空");
        }
        BusinessNumberRule rule = getById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "业务编号规则不存在");
        }
        return rule;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim().toUpperCase();
    }
}
