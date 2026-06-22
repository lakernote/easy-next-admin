package com.laker.admin.infrastructure.observability.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceTest {

    @Test
    void shouldRenderTagAndLimitMaxDepth() {
        Trace trace = new Trace("HTTP", SpanType.Http, "GET /api/users", 2, 0);

        trace.addSpan("service", SpanType.Service, "userId=1");
        trace.addSpan("mapper", SpanType.Mapper, "deptId=2");
        trace.stopSpan();
        trace.stopSpan();
        trace.stopRoot();

        String tree = trace.renderTree();

        assertThat(tree).contains("[Http] HTTP tag=\"GET /api/users\"");
        assertThat(tree).contains("[Service] service tag=\"userId=1\"");
        assertThat(tree).doesNotContain("mapper");
    }

    @Test
    void shouldDropChildNodeBelowMinCost() {
        Trace trace = new Trace("HTTP", SpanType.Http, "GET /api/users", 8, Long.MAX_VALUE);

        trace.addSpan("too-fast", SpanType.Service, "skip=true");
        trace.stopSpan();
        trace.stopRoot();

        assertThat(trace.renderTree())
                .contains("[Http] HTTP tag=\"GET /api/users\"")
                .doesNotContain("too-fast");
    }

    @Test
    void shouldAggregateOnlyConsecutiveDuplicateLeafSpans() throws Exception {
        Trace trace = new Trace("HTTP", SpanType.Http, "GET /api/users", 8, 0);

        trace.addSpan("SysUserMapper#selectById", SpanType.Mapper, "");
        Thread.sleep(1);
        trace.stopSpan();
        trace.addSpan("SysUserMapper#selectById", SpanType.Mapper, "");
        Thread.sleep(2);
        trace.stopSpan();
        trace.addSpan("SysRoleMapper#selectList", SpanType.Mapper, "");
        trace.stopSpan();
        trace.addSpan("SysUserMapper#selectById", SpanType.Mapper, "");
        trace.stopSpan();
        trace.stopRoot();

        String tree = trace.renderTree();

        assertThat(tree)
                .contains("[Mapper] SysUserMapper#selectById [count=2 total=")
                .contains(" min=")
                .contains(" max=")
                .contains("] ")
                .contains("[Mapper] SysRoleMapper#selectList ")
                .doesNotContain("tag=\"\"");
        assertThat(occurrences(tree, "SysUserMapper#selectById")).isEqualTo(2);
    }

    private static int occurrences(String text, String pattern) {
        return text.split(java.util.regex.Pattern.quote(pattern), -1).length - 1;
    }
}
