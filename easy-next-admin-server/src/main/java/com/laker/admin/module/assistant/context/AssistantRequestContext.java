package com.laker.admin.module.assistant.context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户请求进入企业助手主循环前的稳定上下文快照。
 *
 * <p>这里不做意图识别、不选择工具，也不解析“今天下午”这类业务槽位；它只固定本次请求的用户、
 * 时间、链路和消息，避免后续组件各自读取 ThreadLocal 或系统时间导致判断不一致。</p>
 *
 * <p>一次真实请求的上下文示例：</p>
 * <pre>{@code
 * {
 *   "runId": "f29f674f87464e1daefbac7d536ca529",
 *   "traceId": "5d5e08ceee1e44cbb788814aa26d6f49",
 *   "conversationId": 88202607140001,
 *   "normalizedMessage": "请分别处理：\n  1. 查询我的待办\n  2. 今天下午请假",
 *   "zoneId": "Asia/Shanghai",
 *   "requestInstant": "2026-07-14T08:10:30Z",
 *   "user": {
 *     "sessionId": 9001,
 *     "userId": 1001,
 *     "userName": "zhangsan",
 *     "nickName": "张三",
 *     "deptId": 2001,
 *     "deptName": "产品部",
 *     "deptIds": [2001, 2002],
 *     "roles": ["employee"],
 *     "roleNames": ["员工"],
 *     "permissions": ["assistant:chat", "workflow:task:list"],
 *     "superAdmin": false
 *   }
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code runId}：一次 Agent Turn 的唯一编号，用于关联本轮 Plan、模型调用和 Tool 执行。</li>
 *   <li>{@code traceId}：整个 HTTP/异步调用链的编号，同一链路上的日志共用它。</li>
 *   <li>{@code conversationId}：多轮会话编号，用于读取同一份版本化 Conversation State。</li>
 *   <li>{@code normalizedMessage}：只统一换行并删除首尾空白，内部列表、缩进和多行结构保持不变。</li>
 *   <li>{@code requestInstant + zoneId}：本轮唯一时间基准；{@link #today()} 和 {@link #now()} 均由它们推导。</li>
 *   <li>{@code user}：当前登录人的不可变身份与授权快照；Tool 是否可见和可执行仍由权限策略确定。</li>
 * </ul>
 */
public record AssistantRequestContext(
        String runId,
        String traceId,
        Long conversationId,
        String normalizedMessage,
        ZoneId zoneId,
        Instant requestInstant,
        AssistantUserContext user
) {
    /** conversationId 会传给浏览器，必须保持在 JavaScript Number 的安全整数范围内。 */
    public static final long MAX_SAFE_CONVERSATION_ID = 9_007_199_254_740_991L;

    public AssistantRequestContext {
        runId = requireText(runId, "runId");
        traceId = requireText(traceId, "traceId");
        if (conversationId == null
                || conversationId <= 0
                || conversationId > MAX_SAFE_CONVERSATION_ID) {
            throw new IllegalArgumentException(
                    "conversationId must be a positive JavaScript-safe integer"
            );
        }
        normalizedMessage = requireText(normalizedMessage, "normalizedMessage");
        zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        requestInstant = Objects.requireNonNull(requestInstant, "requestInstant must not be null");
        user = Objects.requireNonNull(user, "user must not be null");
    }

    /** 当前日期由同一个请求时间点和时区推导，避免上下文中保存互相矛盾的时间字段。 */
    public LocalDate today() {
        return LocalDateTime.ofInstant(requestInstant, zoneId).toLocalDate();
    }

    /** 当前本地时间由同一个请求时间点和时区推导。 */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(requestInstant, zoneId);
    }

    /**
     * 当前登录人的最小权限快照。
     *
     * <p>后续 ToolVisibilityPolicy 只能基于这个快照判断工具是否可见，权限判断不能散落到 Prompt。</p>
     *
     * <p>permissions 和 superAdmin 是本轮确定性 Tool 授权所需的不可变快照；角色和部门只作为
     * 企业对话上下文，不能代替 ToolVisibilityPolicy 和 ToolExecutionPolicy 的权限判断。</p>
     *
     * <ul>
     *   <li>{@code sessionId/userId}：认证会话与稳定用户标识；会话状态优先按 userId 隔离。</li>
     *   <li>{@code userName/nickName}：账号名与展示名，只用于当前用户语境和回复展示。</li>
     *   <li>{@code deptId/deptName/deptIds}：当前部门及数据范围内部门快照，不代表接口已经获得授权。</li>
     *   <li>{@code roles/roleNames}：角色编码和展示名，用于企业语境，不直接决定 Tool 可见性。</li>
     *   <li>{@code permissions/superAdmin}：本轮 Tool 可见性与执行前复核使用的确定性授权证据。</li>
     * </ul>
     */
    public record AssistantUserContext(
            Long sessionId,
            Long userId,
            String userName,
            String nickName,
            Long deptId,
            String deptName,
            Set<Long> deptIds,
            List<String> roles,
            List<String> roleNames,
            List<String> permissions,
            boolean superAdmin
    ) {
        public AssistantUserContext {
            deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
            roles = roles == null ? List.of() : List.copyOf(roles);
            roleNames = roleNames == null ? List.of() : List.copyOf(roleNames);
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
