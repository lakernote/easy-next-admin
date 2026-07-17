package com.laker.admin.module.assistant;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AssistantTestFixtures {
    private AssistantTestFixtures() {
    }

    public static AssistantRequestContext requestContext(String message) {
        return requestContext(message, false, "assistant:chat");
    }

    public static AssistantRequestContext requestContext(String message, String... permissions) {
        return requestContext(message, false, permissions);
    }

    public static AssistantRequestContext superAdminRequestContext(String message) {
        return requestContext(message, true, "assistant:chat");
    }

    private static AssistantRequestContext requestContext(String message, boolean superAdmin, String... permissions) {
        List<String> permissionList = Arrays.stream(permissions)
                .filter(permission -> permission != null && !permission.isBlank())
                .collect(Collectors.toList());
        return new AssistantRequestContext(
                "run-agent-001",
                "trace-agent-001",
                88L,
                message,
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-07-07T06:30:00Z"),
                new AssistantRequestContext.AssistantUserContext(
                        9001L,
                        1001L,
                        "zhangsan",
                        "张三",
                        2001L,
                        "产品部",
                        Set.of(2001L),
                        List.of("employee"),
                        List.of("员工"),
                        permissionList,
                        superAdmin
                )
        );
    }
}
