package com.laker.admin.module.workflow.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class WorkflowConditionEvaluator {
    private final WorkflowExpressionEvaluator expressionEvaluator;

    public WorkflowConditionEvaluator(WorkflowExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    public boolean matches(WorkflowConditionType type, String expression, Map<String, Object> variables) {
        type = type == null ? WorkflowConditionType.ALWAYS : type;
        if (type == WorkflowConditionType.ALWAYS) {
            return true;
        }
        if (type != WorkflowConditionType.EXPRESSION || !StringUtils.hasText(expression)) {
            return false;
        }
        return expressionEvaluator.evaluate(expression, variables);
    }
}
