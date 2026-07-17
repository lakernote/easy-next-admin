package com.laker.admin.module.workflow.support;

import java.util.Map;

public interface WorkflowExpressionEvaluator {
    boolean evaluate(String expression, Map<String, Object> variables);

    default void validate(String expression) {
    }
}
