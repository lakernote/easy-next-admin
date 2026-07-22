package com.laker.admin.module.assistant.runtime.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.config.AssistantModelProperties;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.trace.AssistantModelCallTiming;
import com.laker.admin.module.assistant.trace.AssistantRunTrace;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import com.laker.admin.module.assistant.trace.AssistantTokenUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Spring AI ChatModel 的 Tool Calling 协议适配器。
 *
 * <p>Spring AI 只处理模型、消息和 Tool Schema 协议；Tool 的权限、执行与循环
 * 仍由 {@code AgentLoop} 控制，因此显式关闭 Spring AI 内部 Tool 执行。</p>
 */
public class SpringAiToolCallingModelClient implements ToolCallingModelClient {
    private static final int MAX_FUNCTION_NAME_LENGTH = 64;
    private static final TypeReference<Map<String, Object>> ARGUMENT_TYPE = new TypeReference<>() {
    };

    private final ChatModel chatModel;
    private final AssistantModelProperties properties;
    private final AssistantRunTraceRecorder traceRecorder;
    private final EasyJsonCodec jsonCodec;

    public SpringAiToolCallingModelClient(ChatModel chatModel,
                                          AssistantModelProperties properties,
                                          AssistantRunTraceRecorder traceRecorder,
                                          EasyJsonCodec jsonCodec) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.traceRecorder = traceRecorder;
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
    }

    @Override
    public ToolCallingResponse call(ToolCallingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long callStartedNanos = System.nanoTime();
        long preparationFinishedNanos = callStartedNanos;
        long providerStartedNanos = 0;
        long providerFinishedNanos = 0;
        long responseProcessingStartedNanos = 0;
        long responseProcessingFinishedNanos = 0;
        AssistantTokenUsage usage = AssistantTokenUsage.ZERO;
        try {
            ToolNameMapping toolNames = ToolNameMapping.from(request.toolNameMappingTools());
            List<ToolCallback> callbacks = request.tools().stream()
                    .map(definition -> new DefinitionOnlyToolCallback(definition, toolNames, jsonCodec))
                    .map(ToolCallback.class::cast)
                    .toList();
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    .model(properties.getModel())
                    .temperature(properties.getTemperature())
                    .maxTokens(effectiveMaxOutputTokens(request))
                    // 提供商仅对纯只读工具面启用并行 Tool Calls；含写 Tool 时由 Runtime 串行调度并保留审批边界。
                    .parallelToolCalls(callbacks.isEmpty() || request.tools().stream().allMatch(ToolDefinition::readOnly))
                    .internalToolExecutionEnabled(false)
                    .toolCallbacks(callbacks);
            if (request.outputSchema() != null) {
                options.responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(ResponseFormat.JsonSchema.builder()
                                .name(request.outputSchema().name())
                                .schema(request.outputSchema().schema())
                                .strict(true)
                                .build())
                        .build());
            }
            if (!callbacks.isEmpty()) {
                options.toolChoice("auto");
            }
            Prompt prompt = new Prompt(toMessages(request.messages(), toolNames), options.build());
            preparationFinishedNanos = System.nanoTime();

            providerStartedNanos = preparationFinishedNanos;
            ChatResponse response = chatModel.call(prompt);
            providerFinishedNanos = System.nanoTime();

            responseProcessingStartedNanos = providerFinishedNanos;
            ToolCallingResponse result = toCoreResponse(response, toolNames, request.outputSchema());
            usage = tokenUsage(response);
            if (!result.hasToolCalls() && looksLikeTextualToolCall(result.content())) {
                throw ModelCallException.protocol("模型把 Tool Call 作为普通文本返回");
            }
            if (!StringUtils.hasText(result.content())
                    && !result.hasToolCalls()
                    && isNormalContentFinish(result.finishReason())) {
                throw ModelCallException.protocol("Tool Calling 模型返回空内容");
            }
            responseProcessingFinishedNanos = System.nanoTime();
            record(
                    timing(callStartedNanos, preparationFinishedNanos, providerStartedNanos, providerFinishedNanos,
                            responseProcessingStartedNanos, responseProcessingFinishedNanos),
                    "OK",
                    usage,
                    request,
                    providerResponseContent(response, result),
                    result,
                    null
            );
            return result;
        } catch (RuntimeException ex) {
            ModelCallException failure = ModelCallException.classify(ex);
            long failedAtNanos = System.nanoTime();
            if (providerStartedNanos == 0) {
                preparationFinishedNanos = failedAtNanos;
            }
            if (providerStartedNanos > 0 && providerFinishedNanos == 0) {
                providerFinishedNanos = failedAtNanos;
            }
            if (responseProcessingStartedNanos > 0 && responseProcessingFinishedNanos == 0) {
                responseProcessingFinishedNanos = failedAtNanos;
            }
            record(
                    timing(callStartedNanos, preparationFinishedNanos, providerStartedNanos, providerFinishedNanos,
                            responseProcessingStartedNanos, responseProcessingFinishedNanos),
                    "FAILED",
                    usage,
                    request,
                    null,
                    null,
                    errorMessage(failure)
            );
            throw failure;
        }
    }

    private int effectiveMaxOutputTokens(ToolCallingRequest request) {
        if (request.maxOutputTokens() == null) {
            return properties.getMaxTokens();
        }
        return Math.min(properties.getMaxTokens(), request.maxOutputTokens());
    }

    private List<Message> toMessages(List<ToolCallingMessage> messages, ToolNameMapping toolNames) {
        Map<String, String> toolNamesByCallId = new LinkedHashMap<>();
        messages.stream()
                .flatMap(message -> message.toolCalls().stream())
                .filter(call -> StringUtils.hasText(call.id()))
                .forEach(call -> toolNamesByCallId.put(call.id(), toolNames.providerName(call.name())));
        return messages.stream()
                .map(message -> toMessage(message, toolNames, toolNamesByCallId))
                .toList();
    }

    private Message toMessage(ToolCallingMessage message,
                              ToolNameMapping toolNames,
                              Map<String, String> toolNamesByCallId) {
        String content = message.content() == null ? "" : message.content();
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(content);
            case USER -> new UserMessage(content);
            case ASSISTANT -> AssistantMessage.builder()
                    .content(content)
                    .toolCalls(message.toolCalls().stream()
                            .map(call -> new AssistantMessage.ToolCall(
                                    call.id(),
                                    "function",
                                    toolNames.providerName(call.name()),
                                    jsonCodec.toJson(call.arguments())
                            ))
                            .toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId(),
                            toolNamesByCallId.getOrDefault(message.toolCallId(), "tool"),
                            content
                    )))
                    .build();
        };
    }

    private ToolCallingResponse toCoreResponse(ChatResponse response,
                                               ToolNameMapping toolNames,
                                               ToolCallingOutputSchema outputSchema) {
        Generation generation = response == null ? null : response.getResult();
        AssistantMessage output = generation == null ? null : generation.getOutput();
        if (output == null) {
            return new ToolCallingResponse(null, List.of(), "");
        }
        List<ToolCall> calls = output.getToolCalls().stream()
                .filter(call -> StringUtils.hasText(call.name()))
                .map(call -> {
                    ParsedToolArguments parsedArguments = parseArguments(call.arguments());
                    return new ToolCall(
                            call.id(),
                            toolNames.canonicalName(call.name()),
                            parsedArguments.arguments(),
                            parsedArguments.error()
                    );
                })
                .toList();
        String finishReason = generation.getMetadata() == null
                ? ""
                : generation.getMetadata().getFinishReason();
        ModelFinishReason normalizedFinishReason = ModelFinishReason.fromProvider(finishReason);
        Map<String, Object> structuredContent = calls.isEmpty()
                && outputSchema != null
                && isNormalContentFinish(normalizedFinishReason)
                ? parseStructuredContent(output.getText())
                : Map.of();
        return new ToolCallingResponse(
                output.getText(),
                calls,
                normalizedFinishReason,
                finishReason,
                structuredContent
        );
    }

    private Map<String, Object> parseStructuredContent(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = jsonCodec.fromJson(content, ARGUMENT_TYPE);
            if (parsed == null) {
                throw ModelCallException.protocol("Structured Output 不是 JSON 对象");
            }
            return parsed;
        } catch (ModelCallException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ModelCallException.protocol("Structured Output 不是合法 JSON 对象");
        }
    }

    private static boolean isNormalContentFinish(ModelFinishReason finishReason) {
        return finishReason == ModelFinishReason.STOP || finishReason == ModelFinishReason.UNSPECIFIED;
    }

    /**
     * 兼容供应商在 Tool 面关闭后偶尔返回的伪 XML Tool Call。
     *
     * <p>只拒绝整个回复都是明确调用信封的情况，不扫描普通正文中的工具术语，也不承担事实审查。</p>
     */
    private static boolean looksLikeTextualToolCall(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalized = content.trim().toLowerCase(java.util.Locale.ROOT);
        return (normalized.startsWith("<tool_call>") && normalized.endsWith("</tool_call>"))
                || (normalized.startsWith("<function_call>") && normalized.endsWith("</function_call>"));
    }

    private ParsedToolArguments parseArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return ParsedToolArguments.valid(Map.of());
        }
        try {
            Map<String, Object> parsed = jsonCodec.fromJson(arguments, ARGUMENT_TYPE);
            return parsed == null
                    ? ParsedToolArguments.invalid()
                    : ParsedToolArguments.valid(parsed);
        } catch (RuntimeException ignored) {
            // 不静默降级为空参数：否则只有可选参数的 Tool 可能被错误执行。
            return ParsedToolArguments.invalid();
        }
    }

    private void record(AssistantModelCallTiming timing,
                        String status,
                        AssistantTokenUsage usage,
                        ToolCallingRequest request,
                        Object providerResponse,
                        ToolCallingResponse response,
                        String error) {
        if (traceRecorder == null) {
            return;
        }
        traceRecorder.recordModelCall(
                request.operationName(),
                properties.getModel(),
                status,
                timing,
                usage,
                traceRecorder.capturePayload(requestMetadata(request), modelRequestContent(request)),
                traceRecorder.capturePayload(responseMetadata(response), providerResponse),
                traceRecorder.sanitizeErrorMessage(error)
        );
    }

    private Map<String, Object> requestMetadata(ToolCallingRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operationName", request.operationName());
        metadata.put("messageCount", request.messages().size());
        metadata.put("messageRoles", request.messages().stream().map(message -> message.role().name()).toList());
        metadata.put("toolCount", request.tools().size());
        metadata.put("toolNames", request.tools().stream().map(ToolDefinition::name).toList());
        metadata.put("maxOutputTokens", effectiveMaxOutputTokens(request));
        metadata.put("outputSchema", request.outputSchema() == null ? "" : request.outputSchema().name());
        return Map.copyOf(metadata);
    }

    private static Map<String, Object> responseMetadata(ToolCallingResponse response) {
        if (response == null) {
            return Map.of("present", false);
        }
        return Map.of(
                "present", true,
                "finishReason", response.finishReason().name(),
                "providerFinishReason", response.providerFinishReason(),
                "contentLength", response.content() == null ? 0 : response.content().length(),
                "toolCallCount", response.toolCalls().size(),
                "toolCalls", response.toolCalls().stream().map(call -> Map.of(
                        "toolCallId", call.id(),
                        "toolName", call.name(),
                        "argumentKeys", call.arguments().keySet(),
                        "argumentError", call.hasArgumentError()
                )).toList()
        );
    }

    private Map<String, Object> modelRequestContent(ToolCallingRequest request) {
        ToolNameMapping toolNames = ToolNameMapping.from(request.toolNameMappingTools());
        boolean parallelToolCalls = request.tools().isEmpty()
                || request.tools().stream().allMatch(ToolDefinition::readOnly);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("model", properties.getModel());
        options.put("temperature", properties.getTemperature());
        options.put("maxOutputTokens", effectiveMaxOutputTokens(request));
        options.put("toolChoice", request.tools().isEmpty() ? "none" : "auto");
        options.put("parallelToolCalls", parallelToolCalls);
        options.put("internalToolExecutionEnabled", false);
        options.put("responseFormat", request.outputSchema() == null ? "text" : "json_schema");
        return Map.of(
                "messages", modelVisibleMessages(request, toolNames),
                "tools", request.tools().stream().map(definition -> Map.of(
                        "name", toolNames.providerName(definition.name()),
                        "description", toolDescription(definition),
                        "inputSchema", inputSchema(definition)
                )).toList(),
                "options", Map.copyOf(options)
        );
    }

    private List<Map<String, Object>> modelVisibleMessages(ToolCallingRequest request,
                                                            ToolNameMapping toolNames) {
        return request.messages().stream().map(message -> {
            Map<String, Object> visible = new LinkedHashMap<>();
            visible.put("role", message.role().name().toLowerCase());
            visible.put("content", message.content() == null ? "" : message.content());
            if (!message.toolCalls().isEmpty()) {
                visible.put("toolCalls", message.toolCalls().stream().map(call -> {
                    Map<String, Object> toolCall = new LinkedHashMap<>();
                    toolCall.put("id", call.id() == null ? "" : call.id());
                    toolCall.put("type", "function");
                    toolCall.put("name", toolNames.providerName(call.name()));
                    toolCall.put("arguments", jsonCodec.toJson(call.arguments()));
                    return toolCall;
                }).toList());
            }
            if (StringUtils.hasText(message.toolCallId())) {
                visible.put("toolCallId", message.toolCallId());
            }
            return Map.copyOf(visible);
        }).toList();
    }

    private static Map<String, Object> providerResponseContent(ChatResponse providerResponse,
                                                               ToolCallingResponse runtimeResponse) {
        Generation generation = providerResponse == null ? null : providerResponse.getResult();
        AssistantMessage output = generation == null ? null : generation.getOutput();
        Map<String, Object> providerOutput = new LinkedHashMap<>();
        providerOutput.put("text", output == null || output.getText() == null ? "" : output.getText());
        providerOutput.put("toolCalls", output == null ? List.of() : output.getToolCalls().stream().map(call -> {
            Map<String, Object> toolCall = new LinkedHashMap<>();
            toolCall.put("id", call.id() == null ? "" : call.id());
            toolCall.put("name", call.name() == null ? "" : call.name());
            toolCall.put("arguments", call.arguments() == null ? "" : call.arguments());
            return Map.copyOf(toolCall);
        }).toList());
        String providerFinishReason = generation == null || generation.getMetadata() == null
                ? ""
                : generation.getMetadata().getFinishReason();
        providerOutput.put("finishReason", providerFinishReason == null ? "" : providerFinishReason);
        return Map.of(
                "providerOutput", Map.copyOf(providerOutput),
                "runtimeNormalizedResponse", runtimeResponse
        );
    }

    private static AssistantModelCallTiming timing(long callStartedNanos,
                                                   long preparationFinishedNanos,
                                                   long providerStartedNanos,
                                                   long providerFinishedNanos,
                                                   long responseProcessingStartedNanos,
                                                   long responseProcessingFinishedNanos) {
        long finishedNanos = Math.max(
                Math.max(preparationFinishedNanos, providerFinishedNanos),
                responseProcessingFinishedNanos
        );
        return AssistantModelCallTiming.synchronous(
                elapsedMillis(callStartedNanos, finishedNanos),
                elapsedMillis(callStartedNanos, preparationFinishedNanos),
                elapsedMillis(providerStartedNanos, providerFinishedNanos),
                elapsedMillis(responseProcessingStartedNanos, responseProcessingFinishedNanos)
        );
    }

    private static long elapsedMillis(long startedNanos, long finishedNanos) {
        if (startedNanos <= 0 || finishedNanos < startedNanos) {
            return 0;
        }
        return AssistantRunTrace.toMillis(finishedNanos - startedNanos);
    }

    private static AssistantTokenUsage tokenUsage(ChatResponse response) {
        Usage usage = response == null || response.getMetadata() == null
                ? null
                : response.getMetadata().getUsage();
        if (usage == null) {
            return AssistantTokenUsage.ZERO;
        }
        int cachedPromptTokens = 0;
        int reasoningTokens = 0;
        if (usage.getNativeUsage() instanceof OpenAiApi.Usage nativeUsage) {
            if (nativeUsage.promptTokensDetails() != null) {
                cachedPromptTokens = safe(nativeUsage.promptTokensDetails().cachedTokens());
            }
            if (nativeUsage.completionTokenDetails() != null) {
                reasoningTokens = safe(nativeUsage.completionTokenDetails().reasoningTokens());
            }
        }
        return new AssistantTokenUsage(
                safe(usage.getPromptTokens()),
                safe(usage.getCompletionTokens()),
                safe(usage.getTotalTokens()),
                cachedPromptTokens,
                reasoningTokens
        );
    }

    private static int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static String errorMessage(RuntimeException ex) {
        return ex.getClass().getSimpleName()
                + (StringUtils.hasText(ex.getMessage()) ? ": " + ex.getMessage() : "");
    }

    private static String toolDescription(ToolDefinition definition) {
        return definition.description();
    }

    private static Map<String, Object> inputSchema(ToolDefinition definition) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolInputField field : definition.inputSchema()) {
            Map<String, Object> fieldSchema = new LinkedHashMap<>();
            fieldSchema.put("type", field.type().value());
            if (field.format() != ToolSchemaFormat.NONE) {
                fieldSchema.put("format", field.format().value());
            }
            fieldSchema.put("description", fieldDescription(field));
            if (!field.enumValues().isEmpty()) {
                fieldSchema.put("enum", field.enumValues());
            }
            properties.put(field.name(), fieldSchema);
            if (field.required()) {
                required.add(field.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static String fieldDescription(ToolInputField field) {
        return StringUtils.hasText(field.description()) ? field.description() : field.displayName();
    }

    private record ParsedToolArguments(Map<String, Object> arguments, String error) {
        private static ParsedToolArguments valid(Map<String, Object> arguments) {
            return new ParsedToolArguments(arguments, "");
        }

        private static ParsedToolArguments invalid() {
            return new ParsedToolArguments(Map.of(), "Tool arguments 不是合法 JSON 对象");
        }
    }

    private record DefinitionOnlyToolCallback(org.springframework.ai.tool.definition.ToolDefinition definition) implements ToolCallback {
        DefinitionOnlyToolCallback(ToolDefinition definition,
                                   ToolNameMapping toolNames,
                                   EasyJsonCodec jsonCodec) {
            this(org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name(toolNames.providerName(definition.name()))
                    .description(toolDescription(definition))
                    .inputSchema(jsonCodec.toJson(inputSchema(definition)))
                    .build());
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new IllegalStateException("Tool 执行必须由企业 Agent Runtime 控制");
        }
    }

    private record ToolNameMapping(Map<String, String> canonicalToProvider,
                                   Map<String, String> providerToCanonical) {
        static ToolNameMapping from(List<ToolDefinition> definitions) {
            Map<String, String> canonicalToProvider = new LinkedHashMap<>();
            Map<String, String> providerToCanonical = new LinkedHashMap<>();
            for (ToolDefinition definition : definitions) {
                String providerName = uniqueProviderName(definition.name(), providerToCanonical);
                canonicalToProvider.put(definition.name(), providerName);
                providerToCanonical.put(providerName, definition.name());
            }
            return new ToolNameMapping(Map.copyOf(canonicalToProvider), Map.copyOf(providerToCanonical));
        }

        String providerName(String canonicalName) {
            return canonicalToProvider.getOrDefault(canonicalName, providerSafeName(canonicalName));
        }

        String canonicalName(String providerName) {
            return providerToCanonical.getOrDefault(providerName, providerName);
        }

        private static String providerSafeName(String value) {
            String source = StringUtils.hasText(value) ? value.trim() : "tool";
            StringBuilder safe = new StringBuilder(source.length());
            for (int index = 0; index < source.length(); index++) {
                char current = source.charAt(index);
                boolean asciiLetterOrDigit = (current >= 'a' && current <= 'z')
                        || (current >= 'A' && current <= 'Z')
                        || (current >= '0' && current <= '9');
                safe.append(asciiLetterOrDigit || current == '_' || current == '-'
                        ? current
                        : '_');
            }
            String result = safe.isEmpty() ? "tool" : safe.toString();
            return result.length() <= MAX_FUNCTION_NAME_LENGTH ? result : withHash(result, source);
        }

        private static String uniqueProviderName(String canonicalName,
                                                 Map<String, String> providerToCanonical) {
            String safeName = providerSafeName(canonicalName);
            String candidate = safeName;
            int salt = 0;
            while (providerToCanonical.containsKey(candidate)
                    && !providerToCanonical.get(candidate).equals(canonicalName)) {
                String source = salt == 0 ? canonicalName : canonicalName + "#" + salt;
                salt++;
                candidate = withHash(safeName, source);
            }
            return candidate;
        }

        private static String withHash(String safeName, String source) {
            String suffix = "_" + Integer.toUnsignedString(source.hashCode(), 36);
            int prefixLength = Math.max(1, MAX_FUNCTION_NAME_LENGTH - suffix.length());
            String prefix = safeName.length() <= prefixLength ? safeName : safeName.substring(0, prefixLength);
            return prefix + suffix;
        }
    }
}
