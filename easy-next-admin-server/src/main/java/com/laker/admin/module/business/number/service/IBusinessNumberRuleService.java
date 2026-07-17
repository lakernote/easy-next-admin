package com.laker.admin.module.business.number.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleQuery;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleRequest;
import com.laker.admin.module.business.number.dto.BusinessNumberRuleView;
import com.laker.admin.module.business.number.entity.BusinessNumberRule;

public interface IBusinessNumberRuleService extends IService<BusinessNumberRule> {
    PageResponse<BusinessNumberRuleView> pageRules(BusinessNumberRuleQuery query);

    BusinessNumberRuleView saveRule(BusinessNumberRuleRequest request);

    BusinessNumberRuleView getRule(Long id);

    boolean deleteRule(Long id);
}
