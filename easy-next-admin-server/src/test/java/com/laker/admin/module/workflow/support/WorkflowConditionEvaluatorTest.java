package com.laker.admin.module.workflow.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowConditionEvaluatorTest {
    private final WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator(new SafeWorkflowExpressionEvaluator());

    @Test
    void shouldMatchNumericExpression() {
        Map<String, Object> variables = Map.of("amount", 6800);

        boolean matched = evaluator.matches(WorkflowConditionType.EXPRESSION, "amount > 5000", variables);

        assertThat(matched).isTrue();
    }

    @Test
    void shouldMatchStringExpression() {
        Map<String, Object> variables = Map.of("businessType", "purchase");

        boolean matched = evaluator.matches(WorkflowConditionType.EXPRESSION, "businessType == 'purchase'", variables);

        assertThat(matched).isTrue();
    }

    @Test
    void shouldRejectUnmatchedExpression() {
        Map<String, Object> variables = Map.of("amount", 1200);

        boolean matched = evaluator.matches(WorkflowConditionType.EXPRESSION, "amount >= 5000", variables);

        assertThat(matched).isFalse();
    }

    @Test
    void shouldMatchPlaceholderSyntaxFromDesigner() {
        Map<String, Object> variables = Map.of("amount", 6800);

        boolean matched = evaluator.matches(WorkflowConditionType.EXPRESSION, "${amount} > 5000", variables);

        assertThat(matched).isTrue();
    }

    @Test
    void shouldTreatAlwaysOrBlankExpressionAsDefaultPath() {
        assertThat(evaluator.matches(WorkflowConditionType.ALWAYS, "", Map.of())).isTrue();
        assertThat(evaluator.matches(null, null, Map.of())).isTrue();
    }
}
