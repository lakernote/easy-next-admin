package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphParserTest {
    private final WorkflowGraphParser parser = new WorkflowGraphParser(new EasyJsonCodec(new ObjectMapper()));

    @Test
    void shouldParseGraphIntoStrongTypedNodesAndEdges() {
        WorkflowGraph graph = parser.parse("""
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"开始","properties":{"nodeType":"START"}},
                    {"id":"submit","type":"rect","text":{"value":"提交申请"},"properties":{"nodeType":"SUBMIT"}},
                    {"id":"check","type":"diamond","text":"条件判断","properties":{"nodeType":"CONDITION"}},
                    {"id":"cc_audit","type":"rect","text":"抄送审计","properties":{"nodeType":"CC"}},
                    {"id":"approve","type":"rect","text":"部门审批","properties":{"nodeType":"APPROVAL","approveType":"ALL"}},
                    {"id":"end","type":"circle","text":"结束","properties":{"nodeType":"END"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"check","targetNodeId":"approve","text":{"value":"通过"},"properties":{"conditionType":"EXPRESSION","conditionExpression":"days <= 3"}}
                  ]
                }
                """);

        assertThat(graph.startKey()).isEqualTo("start");
        assertThat(graph.nodes().get("start").kind()).isEqualTo(WorkflowNodeKind.START);
        assertThat(graph.nodes().get("submit").kind()).isEqualTo(WorkflowNodeKind.SUBMIT);
        assertThat(graph.nodes().get("check").shape()).isEqualTo(WorkflowNodeShape.DIAMOND);
        assertThat(graph.nodes().get("cc_audit").kind()).isEqualTo(WorkflowNodeKind.CC);
        assertThat(graph.nodes().get("approve").propertyText(WorkflowGraphProperty.APPROVE_TYPE)).isEqualTo("ALL");
        assertThat(graph.nodes().get("end").kind()).isEqualTo(WorkflowNodeKind.END);
        assertThat(graph.outgoing().get("check")).singleElement().satisfies(edge -> {
            assertThat(edge.targetKey()).isEqualTo("approve");
            assertThat(edge.conditionType()).isEqualTo(WorkflowConditionType.EXPRESSION);
            assertThat(edge.conditionExpression()).isEqualTo("days <= 3");
            assertThat(edge.label()).isEqualTo("通过");
        });
    }

    @Test
    void shouldRejectNodeWithoutDeclaredNodeType() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"开始"}
                  ],
                  "edges": []
                }
                """))
                .hasMessageContaining("缺少或不支持 nodeType");
    }
}
