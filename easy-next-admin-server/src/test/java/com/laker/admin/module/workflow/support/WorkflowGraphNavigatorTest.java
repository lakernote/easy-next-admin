package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphNavigatorTest {

    private final WorkflowGraphParser parser = new WorkflowGraphParser(new EasyJsonCodec(new ObjectMapper()));
    private final WorkflowGraphNavigator navigator = new WorkflowGraphNavigator(
            new WorkflowConditionEvaluator(new SafeWorkflowExpressionEvaluator()));

    @Test
    void shouldResolveConditionalBranchAndCollectCcNodes() {
        WorkflowGraph.NextStep nextStep = navigator.resolveNextStep(
                parser.parse("""
                        {
                          "nodes": [
                            {"id":"start","type":"circle","text":"Start","properties":{"nodeType":"START"}},
                            {"id":"submit","type":"rect","text":"Submit","properties":{"nodeType":"SUBMIT"}},
                            {"id":"check","type":"diamond","text":"Check","properties":{"nodeType":"CONDITION"}},
                            {"id":"cc_hr","type":"rect","text":"CC","properties":{"nodeType":"CC"}},
                            {"id":"manager","type":"rect","text":"Manager","properties":{"nodeType":"APPROVAL"}},
                            {"id":"end","type":"circle","text":"End","properties":{"nodeType":"END"}}
                          ],
                          "edges": [
                            {"sourceNodeId":"start","targetNodeId":"submit"},
                            {"sourceNodeId":"submit","targetNodeId":"check"},
                            {"sourceNodeId":"check","targetNodeId":"cc_hr","properties":{"conditionType":"EXPRESSION","conditionExpression":"days <= 3"}},
                            {"sourceNodeId":"cc_hr","targetNodeId":"manager"},
                            {"sourceNodeId":"manager","targetNodeId":"end"}
                          ]
                        }
                        """),
                null,
                Map.of("days", 2));

        assertThat(nextStep.approvalNode()).isNotNull();
        assertThat(nextStep.approvalNode().key()).isEqualTo("manager");
        assertThat(nextStep.ccNodes()).extracting(WorkflowGraph.NodeInfo::key).containsExactly("cc_hr");
    }

    @Test
    void shouldUseDefaultEdgeWhenConditionalBranchesDoNotMatch() {
        WorkflowGraph.NextStep nextStep = navigator.resolveNextStep(
                parser.parse("""
                        {
                          "nodes": [
                            {"id":"start","type":"circle","text":"Start","properties":{"nodeType":"START"}},
                            {"id":"check","type":"diamond","text":"Check","properties":{"nodeType":"CONDITION"}},
                            {"id":"office","type":"rect","text":"Office","properties":{"nodeType":"APPROVAL"}},
                            {"id":"manager","type":"rect","text":"Manager","properties":{"nodeType":"APPROVAL"}}
                          ],
                          "edges": [
                            {"sourceNodeId":"start","targetNodeId":"check"},
                            {"sourceNodeId":"check","targetNodeId":"office","properties":{"conditionType":"EXPRESSION","conditionExpression":"amount > 5000"}},
                            {"sourceNodeId":"check","targetNodeId":"manager"}
                          ]
                        }
                        """),
                null,
                Map.of("amount", 1000));

        assertThat(nextStep.approvalNode()).isNotNull();
        assertThat(nextStep.approvalNode().key()).isEqualTo("manager");
    }

    @Test
    void shouldRejectWhenNoBranchMatchesAndNoDefaultEdgeExists() {
        WorkflowGraph graph = parser.parse("""
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"Start","properties":{"nodeType":"START"}},
                    {"id":"check","type":"diamond","text":"Check","properties":{"nodeType":"CONDITION"}},
                    {"id":"office","type":"rect","text":"Office","properties":{"nodeType":"APPROVAL"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"check"},
                    {"sourceNodeId":"check","targetNodeId":"office","properties":{"conditionType":"EXPRESSION","conditionExpression":"amount > 5000"}}
                  ]
                }
                """);

        assertThatThrownBy(() -> navigator.resolveNextStep(graph, null, Map.of("amount", 1000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程分支条件未命中");
    }

    @Test
    void shouldRejectLoopingGraph() {
        WorkflowGraph graph = parser.parse("""
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"Start","properties":{"nodeType":"START"}},
                    {"id":"check","type":"diamond","text":"Check","properties":{"nodeType":"CONDITION"}},
                    {"id":"submit","type":"rect","text":"Submit","properties":{"nodeType":"SUBMIT"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"check"},
                    {"sourceNodeId":"check","targetNodeId":"submit"},
                    {"sourceNodeId":"submit","targetNodeId":"check"}
                  ]
                }
                """);

        assertThatThrownBy(() -> navigator.resolveNextStep(graph, null, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程图存在循环");
    }

    @Test
    void shouldResolvePreviousReturnNodeAcrossSkippableNodes() {
        WorkflowGraph graph = parser.parse("""
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"Start","properties":{"nodeType":"START"}},
                    {"id":"submit","type":"rect","text":"Submit","properties":{"nodeType":"SUBMIT"}},
                    {"id":"check","type":"diamond","text":"Check","properties":{"nodeType":"CONDITION"}},
                    {"id":"manager","type":"rect","text":"Manager","properties":{"nodeType":"APPROVAL"}},
                    {"id":"office","type":"rect","text":"Office","properties":{"nodeType":"APPROVAL"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"submit"},
                    {"sourceNodeId":"submit","targetNodeId":"check"},
                    {"sourceNodeId":"check","targetNodeId":"manager"},
                    {"sourceNodeId":"manager","targetNodeId":"office"}
                  ]
                }
                """);

        assertThat(navigator.resolveReturnNodeKey(graph, "manager", "previous")).isEqualTo("submit");
        assertThat(navigator.resolveReturnNodeKey(graph, "office", "previous")).isEqualTo("manager");
        assertThat(navigator.resolveReturnNodeKey(graph, "office", "submit")).isEqualTo("submit");
        assertThat(navigator.resolveReturnNodeKey(graph, "office", null)).isEqualTo("submit");
    }
}
