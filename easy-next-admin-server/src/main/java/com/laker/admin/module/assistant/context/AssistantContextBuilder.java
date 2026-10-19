package com.laker.admin.module.assistant.context;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import io.github.lakernote.observability.trace.TraceIdContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 构建助手请求上下文。
 *
 * <p>输入：当前登录人、原始用户消息、可选会话 ID。</p>
 * <p>输出：一次请求内稳定不变的 {@link AssistantRequestContext}。</p>
 *
 * <p>注意：这里不能识别意图、不能判断工具权限、不能解析“今天下午”。
 * 它只把后续组件会用到的时间、时区、用户权限快照和消息快照固定下来。</p>
 */
@Component
public class AssistantContextBuilder {
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final Clock clock;
    private final ZoneId zoneId;
    private final Supplier<String> runIdSupplier;
    private final Supplier<Long> conversationIdSupplier;

    public AssistantContextBuilder() {
        this(Clock.system(DEFAULT_ZONE_ID), DEFAULT_ZONE_ID, EasyIdGenerator::uuid32,
                () -> ThreadLocalRandom.current().nextLong(1, AssistantRequestContext.MAX_SAFE_CONVERSATION_ID));
    }

    public AssistantContextBuilder(Clock clock, ZoneId zoneId) {
        this(clock, zoneId, EasyIdGenerator::uuid32);
    }

    public AssistantContextBuilder(Clock clock, ZoneId zoneId, Supplier<String> runIdSupplier) {
        this(clock, zoneId, runIdSupplier,
                () -> ThreadLocalRandom.current().nextLong(1, AssistantRequestContext.MAX_SAFE_CONVERSATION_ID));
    }

    public AssistantContextBuilder(Clock clock,
                                   ZoneId zoneId,
                                   Supplier<String> runIdSupplier,
                                   Supplier<Long> conversationIdSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.runIdSupplier = Objects.requireNonNull(runIdSupplier, "runIdSupplier must not be null");
        this.conversationIdSupplier = Objects.requireNonNull(conversationIdSupplier, "conversationIdSupplier must not be null");
    }

    /**
     * 生成单轮助手请求的输入快照。
     *
     * <p>例如用户发送“我想今天下午请假”，这里只会得到规范化消息、当前日期和时区；
     * “请假”和“今天下午”都由模型通过原生 Tool Call 选择工具并生成结构化参数。</p>
     */
    public AssistantRequestContext build(AuthPrincipal principal, String rawMessage, Long conversationId) {
        if (principal == null) {
            throw new EasyAuthException("当前用户上下文不存在");
        }
        String normalizedMessage = normalizeMessage(rawMessage);
        if (normalizedMessage.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "消息内容不能为空");
        }

        Clock requestClock = clock.withZone(zoneId);
        Instant requestInstant = requestClock.instant();
        String runId = nextRunId();
        String traceId = TraceIdContext.getOrCreateTraceId();
        Long effectiveConversationId = resolveConversationId(conversationId);

        // “今天/下午”等相对表达统一依赖这个请求级时间快照。
        // 没有传 conversationId 时，服务端生成一个并返回给前端，后续补槽位才能读到同一份 Pending State。
        return new AssistantRequestContext(
                runId,
                traceId,
                effectiveConversationId,
                normalizedMessage,
                zoneId,
                requestInstant,
                new AssistantRequestContext.AssistantUserContext(
                        principal.getSessionId(),
                        principal.getUserId(),
                        principal.getUserName(),
                        principal.getNickName(),
                        principal.getDeptId(),
                        principal.getDeptName(),
                        copySet(principal.getDeptIds()),
                        copyList(principal.getRoles()),
                        copyList(principal.getRoleNames()),
                        copyList(principal.getPermissions()),
                        principal.isSuperAdmin()
                )
        );
    }

    private String nextRunId() {
        String runId = runIdSupplier.get();
        return StringUtils.hasText(runId) ? runId : EasyIdGenerator.uuid32();
    }

    private Long nextConversationId() {
        Long conversationId = conversationIdSupplier.get();
        return conversationId == null
                || conversationId <= 0
                || conversationId > AssistantRequestContext.MAX_SAFE_CONVERSATION_ID
                ? ThreadLocalRandom.current().nextLong(1, AssistantRequestContext.MAX_SAFE_CONVERSATION_ID)
                : conversationId;
    }

    private Long resolveConversationId(Long conversationId) {
        if (conversationId == null) {
            return nextConversationId();
        }
        if (conversationId <= 0 || conversationId > AssistantRequestContext.MAX_SAFE_CONVERSATION_ID) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "conversationId 必须是 JavaScript 安全范围内的正整数"
            );
        }
        return conversationId;
    }

    private static String normalizeMessage(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        // 只统一换行并去除首尾空白；正文换行、缩进和列表结构都是模型理解复杂请求的重要信息。
        return rawMessage.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static List<String> copyList(List<String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static Set<Long> copySet(Set<Long> source) {
        return source == null ? Set.of() : Set.copyOf(source);
    }
}
