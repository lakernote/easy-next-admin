package com.laker.admin.module.assistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 读取版本化企业 Agent 场景；只属于测试基线，不进入生产 Runtime。 */
final class AssistantEvalDataset {
    static final String RESOURCE = "/assistant/evals/enterprise-agent-v1.json";

    private AssistantEvalDataset() {
    }

    static EvalSuite load(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        try (InputStream input = AssistantEvalDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("缺少 Assistant eval 数据集：" + RESOURCE);
            }
            return objectMapper.readValue(input, EvalSuite.class);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取 Assistant eval 数据集：" + RESOURCE, ex);
        }
    }

    record EvalSuite(int version, String description, List<EvalCase> cases) {
        EvalSuite {
            description = text(description);
            cases = copy(cases);
        }
    }

    record EvalCase(String id,
                    AssistantEvalCategory category,
                    String description,
                    boolean mutating,
                    List<EvalTurn> turns) {
        EvalCase {
            id = text(id);
            description = text(description);
            turns = copy(turns);
        }
    }

    record EvalTurn(String message, EvalExpectation expect) {
        EvalTurn {
            message = text(message);
            expect = expect == null ? EvalExpectation.empty() : expect;
        }
    }

    /**
     * 一轮的可解释断言。数组表示允许集合或必须包含集合，避免把自然语言结果写成脆弱的全文匹配。
     */
    record EvalExpectation(List<String> allowedPlanStatuses,
                           List<String> allowedExecutionStatuses,
                           List<String> allowedToolNames,
                           List<String> requiredToolNames,
                           List<String> forbiddenToolNames,
                           Map<String, String> requiredArguments,
                           Map<String, String> requiredArgumentsIgnoringWhitespace,
                           List<String> replyContainsAll,
                           List<String> replyContainsAny,
                           List<List<String>> replyContainsAnyGroups,
                           List<String> replyExcludes,
                           boolean replyContainsCurrentDate,
                           boolean replyContainsCurrentWeekday,
                           boolean requireNoTool,
                           Integer maxModelCalls,
                           Integer maxToolCalls,
                           Integer maxPromptTokens,
                           Integer maxTotalTokens,
                           Long maxTotalDurationMs) {
        EvalExpectation {
            allowedPlanStatuses = copy(allowedPlanStatuses);
            allowedExecutionStatuses = copy(allowedExecutionStatuses);
            allowedToolNames = copy(allowedToolNames);
            requiredToolNames = copy(requiredToolNames);
            forbiddenToolNames = copy(forbiddenToolNames);
            requiredArguments = requiredArguments == null ? Map.of() : Map.copyOf(requiredArguments);
            requiredArgumentsIgnoringWhitespace = requiredArgumentsIgnoringWhitespace == null
                    ? Map.of()
                    : Map.copyOf(requiredArgumentsIgnoringWhitespace);
            replyContainsAll = copy(replyContainsAll);
            replyContainsAny = copy(replyContainsAny);
            replyContainsAnyGroups = copyGroups(replyContainsAnyGroups);
            replyExcludes = copy(replyExcludes);
        }

        static EvalExpectation empty() {
            return new EvalExpectation(
                    List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(),
                    List.of(), List.of(), List.of(), List.of(), false, false, false,
                    null, null, null, null, null
            );
        }

        boolean hasMeaningfulAssertion() {
            return replyContainsCurrentDate
                    || replyContainsCurrentWeekday
                    || requireNoTool
                    || maxModelCalls != null
                    || maxToolCalls != null
                    || maxPromptTokens != null
                    || maxTotalTokens != null
                    || maxTotalDurationMs != null
                    || !allowedPlanStatuses.isEmpty()
                    || !allowedExecutionStatuses.isEmpty()
                    || !allowedToolNames.isEmpty()
                    || !requiredToolNames.isEmpty()
                    || !forbiddenToolNames.isEmpty()
                    || !requiredArguments.isEmpty()
                    || !requiredArgumentsIgnoringWhitespace.isEmpty()
                    || !replyContainsAll.isEmpty()
                    || !replyContainsAny.isEmpty()
                    || !replyContainsAnyGroups.isEmpty()
                    || !replyExcludes.isEmpty();
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<List<String>> copyGroups(List<List<String>> groups) {
        return groups == null ? List.of() : groups.stream()
                .map(AssistantEvalDataset::copy)
                .toList();
    }
}
