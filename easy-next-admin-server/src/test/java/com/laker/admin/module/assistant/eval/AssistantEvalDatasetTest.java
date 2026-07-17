package com.laker.admin.module.assistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantEvalDatasetTest {

    @Test
    void shouldProvideVersionedEnterpriseAgentRegressionBaseline() {
        AssistantEvalDataset.EvalSuite suite = AssistantEvalDataset.load(new ObjectMapper());

        assertThat(suite.version()).isEqualTo(1);
        assertThat(suite.description()).isNotBlank();
        assertThat(suite.cases()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(suite.cases())
                .extracting(AssistantEvalDataset.EvalCase::id)
                .doesNotHaveDuplicates()
                .allMatch(id -> id.matches("[a-z0-9][a-z0-9-]+"));
        assertThat(suite.cases())
                .extracting(AssistantEvalDataset.EvalCase::category)
                .containsAll(EnumSet.allOf(AssistantEvalCategory.class));
        assertThat(suite.cases())
                .extracting(AssistantEvalDataset.EvalCase::id)
                .as("真实失败 case 必须保留在版本化回归集中")
                .contains(
                        "personal-process-query-correction",
                        "new-goals-do-not-reuse-stale-detail",
                        "tool-result-must-not-drop-other-subgoals"
                );
        assertThat(suite.cases()).allSatisfy(evalCase -> {
            assertThat(evalCase.description()).as(evalCase.id()).isNotBlank();
            assertThat(evalCase.turns()).as(evalCase.id()).isNotEmpty();
            assertThat(evalCase.turns()).allSatisfy(turn -> {
                assertThat(turn.message()).as(evalCase.id()).isNotBlank();
                assertThat(turn.expect().hasMeaningfulAssertion())
                        .as(evalCase.id() + " 每一轮都应声明至少一个可解释断言")
                        .isTrue();
            });
        });
    }

    @Test
    void shouldKeepDefaultDatasetSafeForSharedLocalDatabase() {
        AssistantEvalDataset.EvalSuite suite = AssistantEvalDataset.load(new ObjectMapper());

        assertThat(suite.cases())
                .filteredOn(AssistantEvalDataset.EvalCase::mutating)
                .as("默认 v1 场景不能确认真实写操作；写入型 eval 应单独建隔离数据集")
                .isEmpty();
    }
}
