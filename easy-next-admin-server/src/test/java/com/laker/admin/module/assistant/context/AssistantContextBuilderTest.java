package com.laker.admin.module.assistant.context;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import io.github.lakernote.observability.trace.TraceIdContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantContextBuilderTest {

    private final AssistantContextBuilder contextBuilder = new AssistantContextBuilder(
            Clock.fixed(Instant.parse("2026-07-07T06:30:00Z"), ZoneId.of("UTC")),
            ZoneId.of("Asia/Shanghai"),
            () -> "run-fixed-001",
            () -> 77L
    );

    @AfterEach
    void clearRequestState() {
        MDC.clear();
    }

    @Test
    void shouldBuildStableRequestContextFromPrincipalMessageAndClock() {
        TraceIdContext.putTraceId("trace-fixed-001");
        AssistantRequestContext context = contextBuilder.build(
                principal(),
                " \r\n请分别处理：\r\n  1. 查询待办\r\n  2. 今天下午请假\t ",
                88L
        );

        assertThat(context.runId()).isEqualTo("run-fixed-001");
        assertThat(context.traceId()).isEqualTo("trace-fixed-001");
        assertThat(context.conversationId()).isEqualTo(88L);
        assertThat(context.normalizedMessage()).isEqualTo("请分别处理：\n  1. 查询待办\n  2. 今天下午请假");
        assertThat(context.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(context.requestInstant()).isEqualTo(Instant.parse("2026-07-07T06:30:00Z"));
        assertThat(context.today()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(context.now()).isEqualTo(LocalDateTime.of(2026, 7, 7, 14, 30));
        assertThat(context.user().userId()).isEqualTo(1001L);
        assertThat(context.user().userName()).isEqualTo("zhangsan");
        assertThat(context.user().nickName()).isEqualTo("张三");
        assertThat(context.user().deptId()).isEqualTo(2001L);
        assertThat(context.user().deptName()).isEqualTo("产品部");
        assertThat(context.user().roles()).containsExactly("employee");
        assertThat(context.user().permissions()).containsExactly("workflow:instance:start");
        assertThat(context.user().superAdmin()).isFalse();
    }

    @Test
    void shouldGenerateConversationIdWhenConversationIsMissing() {
        AssistantRequestContext context = contextBuilder.build(principal(), "查一下我的待办", null);

        assertThat(context.conversationId()).isEqualTo(77L);
    }

    @Test
    void shouldRejectBlankMessageBeforeAgentExecution() {
        assertThatThrownBy(() -> contextBuilder.build(principal(), " \n\t ", null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getMsg()).isEqualTo("消息内容不能为空");
                });
    }

    @Test
    void shouldRejectInvalidClientConversationId() {
        assertThatThrownBy(() -> contextBuilder.build(principal(), "查询待办", 0L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> contextBuilder.build(
                principal(),
                "查询待办",
                AssistantRequestContext.MAX_SAFE_CONVERSATION_ID + 1
        )).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void shouldRejectMissingPrincipalBeforeAgentExecution() {
        assertThatThrownBy(() -> contextBuilder.build(null, "我想今天下午请假", null))
                .isInstanceOfSatisfying(EasyAuthException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).isEqualTo("当前用户上下文不存在");
                });
    }

    @Test
    void shouldDefensivelyCopyUserAuthorizationSnapshot() {
        Set<Long> deptIds = new HashSet<>(Set.of(2001L));
        List<String> roles = new ArrayList<>(List.of("employee"));
        List<String> roleNames = new ArrayList<>(List.of("员工"));
        List<String> permissions = new ArrayList<>(List.of("workflow:task:list"));

        AssistantRequestContext.AssistantUserContext user =
                new AssistantRequestContext.AssistantUserContext(
                        9001L,
                        1001L,
                        "zhangsan",
                        "张三",
                        2001L,
                        "产品部",
                        deptIds,
                        roles,
                        roleNames,
                        permissions,
                        false
                );
        deptIds.add(2002L);
        roles.add("manager");
        roleNames.add("经理");
        permissions.add("workflow:task:approve");

        assertThat(user.deptIds()).containsExactly(2001L);
        assertThat(user.roles()).containsExactly("employee");
        assertThat(user.roleNames()).containsExactly("员工");
        assertThat(user.permissions()).containsExactly("workflow:task:list");
        assertThatThrownBy(() -> user.permissions().add("workflow:task:reject"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidDirectRequestContext() {
        assertThatThrownBy(() -> new AssistantRequestContext(
                "",
                "trace-1",
                88L,
                "查询待办",
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-07-07T06:30:00Z"),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    }

    private static AuthPrincipal principal() {
        return AuthPrincipal.builder()
                .sessionId(9001L)
                .userId(1001L)
                .userName("zhangsan")
                .nickName("张三")
                .deptId(2001L)
                .deptName("产品部")
                .deptIds(Set.of(2001L))
                .roles(List.of("employee"))
                .roleNames(List.of("员工"))
                .permissions(List.of("workflow:instance:start"))
                .superAdmin(false)
                .build();
    }
}
