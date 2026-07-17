package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphValidatorTest {
    private final WorkflowGraphValidator validator = new WorkflowGraphValidator(
            new WorkflowGraphParser(new EasyJsonCodec(new ObjectMapper())),
            new SafeWorkflowExpressionEvaluator());

    @Test
    void shouldAcceptRunnableApprovalGraph() {
        assertThatCode(() -> validator.validateForEnable("""
                {"nodes":[
                  {"id":"start","properties":{"nodeType":"START"}},
                  {"id":"approve","text":"审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"end","properties":{"nodeType":"END"}}
                ],"edges":[
                  {"sourceNodeId":"start","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"end"}
                ]}
                """)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectApprovalNodeWithoutAssigneeRule() {
        assertThatThrownBy(() -> validator.validateForEnable("""
                {"nodes":[
                  {"id":"start","properties":{"nodeType":"START"}},
                  {"id":"approve","text":"经理审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE"}},
                  {"id":"end","properties":{"nodeType":"END"}}
                ],"edges":[
                  {"sourceNodeId":"start","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"end"}
                ]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置有效处理人规则");
    }

    @Test
    void shouldRejectCycleInEnabledGraph() {
        assertThatThrownBy(() -> validator.validateForEnable("""
                {"nodes":[
                  {"id":"start","properties":{"nodeType":"START"}},
                  {"id":"approve","text":"审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"end","properties":{"nodeType":"END"}}
                ],"edges":[
                  {"sourceNodeId":"start","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"end"}
                ]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程图存在循环");
    }

    @Test
    void shouldRejectConditionalBranchWithoutDefaultPath() {
        assertThatThrownBy(() -> validator.validateForEnable("""
                {"nodes":[
                  {"id":"start","properties":{"nodeType":"START"}},
                  {"id":"approve","text":"审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"high","text":"高额审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"low","text":"普通审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"end","properties":{"nodeType":"END"}}
                ],"edges":[
                  {"sourceNodeId":"start","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"high","properties":{"conditionType":"EXPRESSION","conditionExpression":"amount > 5000"}},
                  {"sourceNodeId":"approve","targetNodeId":"low","properties":{"conditionType":"EXPRESSION","conditionExpression":"amount <= 5000"}},
                  {"sourceNodeId":"high","targetNodeId":"end"},
                  {"sourceNodeId":"low","targetNodeId":"end"}
                ]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条件分支需要配置默认路径");
    }

    @Test
    void shouldRejectUnsupportedConditionExpression() {
        assertThatThrownBy(() -> validator.validateForEnable("""
                {"nodes":[
                  {"id":"start","properties":{"nodeType":"START"}},
                  {"id":"approve","text":"审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"high","text":"高额审批","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[1]}},
                  {"id":"end","properties":{"nodeType":"END"}}
                ],"edges":[
                  {"sourceNodeId":"start","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"high","properties":{"conditionType":"EXPRESSION","conditionExpression":"amount >> 5000"}},
                  {"sourceNodeId":"approve","targetNodeId":"end"},
                  {"sourceNodeId":"high","targetNodeId":"end"}
                ]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的条件表达式");
    }
}
