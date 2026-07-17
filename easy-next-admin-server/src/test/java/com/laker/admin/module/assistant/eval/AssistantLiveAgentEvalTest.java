package com.laker.admin.module.assistant.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对已经启动的本地后端执行真实模型 eval。
 *
 * <p>默认 Maven 测试只会跳过本类，不访问网络。显式启用：</p>
 * <pre>{@code
 * mvn -pl easy-next-admin-server \
 *   -Dtest=AssistantLiveAgentEvalTest \
 *   -Deasy.assistant.live-eval=true test
 * }</pre>
 * <p>可用 {@code -Deasy.assistant.eval.case-ids=basic-arithmetic-direct,query-my-tasks} 只运行指定场景。</p>
 */
@Tag("live-model")
class AssistantLiveAgentEvalTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);
    private static final ZoneId EVAL_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldPassVersionedScenariosAgainstRunningAgent() throws Exception {
        Assumptions.assumeTrue(
                booleanSetting("easy.assistant.live-eval", "EASY_ASSISTANT_LIVE_EVAL", false),
                "仅在显式启用时调用本地真实后端和模型"
        );
        String baseUrl = setting(
                "easy.assistant.eval.base-url",
                "EASY_ASSISTANT_EVAL_BASE_URL",
                "http://127.0.0.1:8080"
        ).replaceAll("/+$", "");
        String username = setting("easy.assistant.eval.username", "EASY_ASSISTANT_EVAL_USERNAME", "admin");
        String password = setting("easy.assistant.eval.password", "EASY_ASSISTANT_EVAL_PASSWORD", "admin");
        boolean includeMutating = booleanSetting(
                "easy.assistant.eval.include-mutating",
                "EASY_ASSISTANT_EVAL_INCLUDE_MUTATING",
                false
        );
        int repetitions = boundedIntSetting(
                "easy.assistant.eval.repetitions",
                "EASY_ASSISTANT_EVAL_REPETITIONS",
                1,
                1,
                10
        );

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String accessToken = login(client, baseUrl, username, password);
        AssistantEvalDataset.EvalSuite suite = AssistantEvalDataset.load(JSON);
        Set<String> selectedIds = csvSetting("easy.assistant.eval.case-ids", "EASY_ASSISTANT_EVAL_CASE_IDS");
        List<AssistantEvalDataset.EvalCase> selectedCases = suite.cases().stream()
                .filter(evalCase -> selectedIds.isEmpty() || selectedIds.contains(evalCase.id()))
                .filter(evalCase -> includeMutating || !evalCase.mutating())
                .toList();
        assertThat(selectedCases)
                .as("case-ids 过滤后至少要有一个 eval 场景")
                .isNotEmpty();

        List<String> failures = new ArrayList<>();
        int passedTurns = 0;
        for (AssistantEvalDataset.EvalCase evalCase : selectedCases) {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                Long conversationId = null;
                for (int turnIndex = 0; turnIndex < evalCase.turns().size(); turnIndex++) {
                    AssistantEvalDataset.EvalTurn turn = evalCase.turns().get(turnIndex);
                    JsonNode data = null;
                    try {
                        data = sendMessage(client, baseUrl, accessToken, turn.message(), conversationId);
                        conversationId = nullableLong(data.path("conversationId"));
                        assertTurn(evalCase.id(), turnIndex + 1, turn.expect(), data);
                        passedTurns++;
                        System.out.printf(
                                "EVAL PASS case=%s repetition=%d turn=%d planStatus=%s tool=%s modelCalls=%d toolCalls=%d promptTokens=%d totalTokens=%d durationMs=%d%n",
                                evalCase.id(),
                                repetition,
                                turnIndex + 1,
                                text(data.path("plan").path("status")),
                                text(data.path("plan").path("toolName")),
                                data.path("trace").path("latency").path("modelCallCount").asInt(),
                                data.path("trace").path("latency").path("toolCallCount").asInt(),
                                data.path("trace").path("tokenUsage").path("promptTokens").asInt(),
                                data.path("trace").path("tokenUsage").path("totalTokens").asInt(),
                                data.path("trace").path("totalDurationMs").asLong()
                        );
                    } catch (AssertionError | RuntimeException | IOException | InterruptedException ex) {
                        if (ex instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        failures.add("case=%s repetition=%d turn=%d: %s".formatted(
                                evalCase.id(), repetition, turnIndex + 1,
                                conciseMessage(ex) + "; actual=" + actualResultSummary(data)
                        ));
                        System.out.printf(
                                "EVAL FAIL case=%s repetition=%d turn=%d actual=%s%n",
                                evalCase.id(), repetition, turnIndex + 1, actualResultSummary(data)
                        );
                        break;
                    }
                }
            }
        }

        System.out.printf(
                "EVAL SUMMARY dataset=v%d cases=%d repetitions=%d passedTurns=%d failures=%d%n",
                suite.version(), selectedCases.size(), repetitions, passedTurns, failures.size()
        );
        assertThat(failures)
                .as("真实企业 Agent eval 失败明细")
                .isEmpty();
    }

    private static String login(HttpClient client,
                                String baseUrl,
                                String username,
                                String password) throws IOException, InterruptedException {
        ObjectNode requestBody = JSON.createObjectNode()
                .put("username", username)
                .put("password", password);
        HttpResponse<String> response = post(client, baseUrl + "/api/auth/login", null, requestBody);
        JsonNode root = successfulApiResponse("login", response);
        String accessToken = text(root.path("data").path("accessToken"));
        assertThat(accessToken).as("登录响应必须返回 accessToken").isNotBlank();
        return accessToken;
    }

    private static JsonNode sendMessage(HttpClient client,
                                        String baseUrl,
                                        String accessToken,
                                        String message,
                                        Long conversationId) throws IOException, InterruptedException {
        ObjectNode requestBody = JSON.createObjectNode().put("message", message);
        if (conversationId != null) {
            requestBody.put("conversationId", conversationId);
        }
        HttpResponse<String> response = post(
                client,
                baseUrl + "/api/assistant/messages",
                accessToken,
                requestBody
        );
        return successfulApiResponse("assistant message", response).path("data");
    }

    private static HttpResponse<String> post(HttpClient client,
                                             String url,
                                             String accessToken,
                                             JsonNode body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (accessToken != null && !accessToken.isBlank()) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode successfulApiResponse(String operation, HttpResponse<String> response) {
        try {
            assertThat(response.statusCode()).as(operation + " HTTP status").isEqualTo(200);
            JsonNode root = JSON.readTree(response.body());
            assertThat(root.path("code").asInt(Integer.MIN_VALUE)).as(operation + " business code").isZero();
            assertThat(root.path("data").isMissingNode() || root.path("data").isNull())
                    .as(operation + " response data")
                    .isFalse();
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException(operation + " 返回了无法解析的 JSON", ex);
        }
    }

    private static void assertTurn(String caseId,
                                   int turnNumber,
                                   AssistantEvalDataset.EvalExpectation expect,
                                   JsonNode data) {
        String description = "case=%s turn=%d".formatted(caseId, turnNumber);
        String planStatus = text(data.path("plan").path("status"));
        String executionStatus = text(data.path("execution").path("status"));
        String toolName = text(data.path("plan").path("toolName"));
        String reply = text(data.path("reply"));
        int modelCalls = data.path("trace").path("latency").path("modelCallCount").asInt();
        int toolCalls = data.path("trace").path("latency").path("toolCallCount").asInt();
        int promptTokens = data.path("trace").path("tokenUsage").path("promptTokens").asInt();
        int totalTokens = data.path("trace").path("tokenUsage").path("totalTokens").asInt();
        long totalDurationMs = data.path("trace").path("totalDurationMs").asLong();
        Set<String> actualToolNames = toolNames(data);
        Map<String, String> actualArguments = argumentMap(data);

        assertThat(reply).as(description + " reply").isNotBlank();
        if (!expect.allowedPlanStatuses().isEmpty()) {
            assertThat(expect.allowedPlanStatuses())
                    .as(description + " planStatus=" + planStatus)
                    .contains(planStatus);
        }
        if (!expect.allowedExecutionStatuses().isEmpty()) {
            assertThat(expect.allowedExecutionStatuses())
                    .as(description + " executionStatus=" + executionStatus)
                    .contains(executionStatus);
        }
        if (!expect.allowedToolNames().isEmpty()) {
            assertThat(expect.allowedToolNames())
                    .as(description + " toolName=" + toolName)
                    .contains(toolName);
        }
        assertThat(actualToolNames)
                .as(description + " required tools")
                .containsAll(expect.requiredToolNames());
        if (!expect.forbiddenToolNames().isEmpty()) {
            assertThat(actualToolNames)
                    .as(description + " forbidden tools")
                    .doesNotContainAnyElementsOf(expect.forbiddenToolNames());
        }
        expect.requiredArguments().forEach((key, value) -> assertThat(actualArguments)
                .as(description + " required argument " + key)
                .containsEntry(key, value));
        expect.requiredArgumentsIgnoringWhitespace().forEach((key, value) -> assertThat(
                compactWhitespace(actualArguments.get(key))
        )
                .as(description + " required argument ignoring whitespace " + key)
                .isEqualTo(compactWhitespace(value)));
        expect.replyContainsAll().forEach(fragment -> assertThat(reply)
                .as(description + " reply contains " + fragment)
                .contains(fragment));
        if (!expect.replyContainsAny().isEmpty()) {
            assertThat(expect.replyContainsAny().stream().anyMatch(reply::contains))
                    .as(description + " reply should contain any of " + expect.replyContainsAny())
                    .isTrue();
        }
        expect.replyContainsAnyGroups().forEach(group -> assertThat(group.stream().anyMatch(reply::contains))
                .as(description + " reply should contain any of group " + group)
                .isTrue());
        if (expect.replyContainsCurrentDate()) {
            LocalDate currentDate = LocalDate.now(EVAL_ZONE);
            List<String> dateFragments = List.of(
                    currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    "%d年%d月%d日".formatted(currentDate.getYear(), currentDate.getMonthValue(), currentDate.getDayOfMonth()),
                    "%d月%d日".formatted(currentDate.getMonthValue(), currentDate.getDayOfMonth())
            );
            String compactReply = compactWhitespace(reply);
            assertThat(dateFragments.stream().map(AssistantLiveAgentEvalTest::compactWhitespace)
                            .anyMatch(compactReply::contains))
                    .as(description + " reply should contain current date " + dateFragments)
                    .isTrue();
        }
        if (expect.replyContainsCurrentWeekday()) {
            String currentWeekday = chineseWeekday(LocalDate.now(EVAL_ZONE));
            assertThat(reply)
                    .as(description + " reply contains current weekday " + currentWeekday)
                    .contains(currentWeekday);
        }
        expect.replyExcludes().forEach(fragment -> assertThat(reply)
                .as(description + " reply excludes " + fragment)
                .doesNotContain(fragment));
        if (expect.requireNoTool()) {
            assertThat(toolCalls).as(description + " no tool call").isZero();
            assertThat(actualToolNames).as(description + " no tool names").isEmpty();
        }
        if (expect.maxModelCalls() != null) {
            assertThat(modelCalls).as(description + " model call budget").isLessThanOrEqualTo(expect.maxModelCalls());
        }
        if (expect.maxToolCalls() != null) {
            assertThat(toolCalls).as(description + " tool call budget").isLessThanOrEqualTo(expect.maxToolCalls());
        }
        if (expect.maxPromptTokens() != null) {
            assertThat(promptTokens).as(description + " prompt token budget")
                    .isLessThanOrEqualTo(expect.maxPromptTokens());
        }
        if (expect.maxTotalTokens() != null) {
            assertThat(totalTokens).as(description + " total token budget")
                    .isLessThanOrEqualTo(expect.maxTotalTokens());
        }
        if (expect.maxTotalDurationMs() != null) {
            assertThat(totalDurationMs).as(description + " total duration budget")
                    .isLessThanOrEqualTo(expect.maxTotalDurationMs());
        }
    }

    private static String chineseWeekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private static String compactWhitespace(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder compact = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!Character.isWhitespace(current)) {
                compact.append(current);
            }
        }
        return compact.toString();
    }

    private static Set<String> toolNames(JsonNode data) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode step : data.path("trace").path("steps")) {
            for (JsonNode toolCall : step.path("toolCalls")) {
                String name = text(toolCall.path("name"));
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static Map<String, String> argumentMap(JsonNode data) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (JsonNode argument : data.path("plan").path("filledArguments")) {
            String key = text(argument.path("key"));
            if (!key.isBlank()) {
                arguments.put(key, text(argument.path("value")));
            }
        }
        return arguments;
    }

    private static Long nullableLong(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }

    private static String setting(String propertyName, String environmentName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.isBlank() ? defaultValue : environmentValue.trim();
    }

    private static boolean booleanSetting(String propertyName,
                                          String environmentName,
                                          boolean defaultValue) {
        return Boolean.parseBoolean(setting(propertyName, environmentName, String.valueOf(defaultValue)));
    }

    private static int boundedIntSetting(String propertyName,
                                         String environmentName,
                                         int defaultValue,
                                         int minValue,
                                         int maxValue) {
        String value = setting(propertyName, environmentName, String.valueOf(defaultValue));
        try {
            return Math.max(minValue, Math.min(maxValue, Integer.parseInt(value)));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(propertyName + " 必须是整数", ex);
        }
    }

    private static Set<String> csvSetting(String propertyName, String environmentName) {
        String value = setting(propertyName, environmentName, "");
        if (value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String conciseMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * 失败时只输出定位所需的结构化摘要，避免把整份 Prompt、Trace 和 Tool 结果写入测试日志。
     */
    private static String actualResultSummary(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return "<no-response>";
        }
        ObjectNode summary = JSON.createObjectNode();
        summary.put("planStatus", text(data.path("plan").path("status")));
        summary.put("executionStatus", text(data.path("execution").path("status")));
        summary.put("toolName", text(data.path("plan").path("toolName")));
        summary.set("toolNames", JSON.valueToTree(toolNames(data)));
        summary.set("arguments", JSON.valueToTree(argumentMap(data)));
        summary.put("reply", abbreviate(text(data.path("reply")), 500));
        summary.put("modelCalls", data.path("trace").path("latency").path("modelCallCount").asInt());
        summary.put("toolCalls", data.path("trace").path("latency").path("toolCallCount").asInt());
        return summary.toString();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
