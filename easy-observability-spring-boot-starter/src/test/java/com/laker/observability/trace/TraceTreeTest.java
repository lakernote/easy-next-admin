package com.laker.observability.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceTreeTest {

    @Test
    void shouldRenderTagAndLimitMaxDepth() {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /api/users", 2, 0);

        trace.addSpan("service", SpanKind.SERVICE, "userId=1");
        trace.addSpan("mapper", SpanKind.DATABASE, "deptId=2");
        trace.stopSpan();
        trace.stopSpan();
        trace.stopRoot();

        String tree = trace.renderTree();

        assertThat(tree).contains("[Http] HTTP tag=\"GET /api/users\"");
        assertThat(tree).contains("[Service] service tag=\"userId=1\"");
        assertThat(tree).doesNotContain("mapper").contains("TRACE_TRUNCATED omitted=1");
        assertThat(trace.incidentReasons()).containsExactly("TRACE_TRUNCATED");
    }

    @Test
    void shouldResumeRecordingAfterReturningFromSkippedDepth() {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /api/users", 2, 0);

        trace.addSpan("first", SpanKind.SERVICE, "");
        trace.addSpan("too-deep", SpanKind.DATABASE, "");
        trace.stopSpan();
        trace.stopSpan();
        trace.addSpan("second", SpanKind.SERVICE, "");
        trace.stopSpan();
        trace.stopRoot();

        assertThat(trace.renderTree())
                .contains("first", "second", "TRACE_TRUNCATED omitted=1")
                .doesNotContain("too-deep");
        assertThat(trace.incidentReasons()).containsExactly("TRACE_TRUNCATED");
    }

    @Test
    void shouldDropChildNodeBelowMinCost() {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /api/users", 8, Long.MAX_VALUE);

        trace.addSpan("too-fast", SpanKind.SERVICE, "skip=true");
        trace.stopSpan();
        trace.stopRoot();

        assertThat(trace.renderTree())
                .contains("[Http] HTTP tag=\"GET /api/users\"")
                .doesNotContain("too-fast");
    }

    @Test
    void shouldAggregateOnlyConsecutiveDuplicateLeafSpans() throws Exception {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /api/users", 8, 0);

        trace.addSpan("SysUserMapper#selectById", SpanKind.DATABASE, "");
        Thread.sleep(1);
        trace.stopSpan();
        trace.addSpan("SysUserMapper#selectById", SpanKind.DATABASE, "");
        Thread.sleep(2);
        trace.stopSpan();
        trace.addSpan("SysRoleMapper#selectList", SpanKind.DATABASE, "");
        trace.stopSpan();
        trace.addSpan("SysUserMapper#selectById", SpanKind.DATABASE, "");
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

    @Test
    void shouldKeepLongTransactionAsRootLinkedTransactionSpan() {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /orders", 8, Long.MAX_VALUE);

        Span transaction = trace.addDetachedSpan("OrderService#create", SpanKind.TRANSACTION, "write");
        transaction.stop();
        trace.markSlow("LONG_TRANSACTION");
        trace.stopRoot();

        assertThat(trace.renderTree()).contains("[Transaction] OrderService#create tag=\"write\"");
        assertThat(trace.slow()).isTrue();
        assertThat(trace.incidentReasons()).containsExactly("LONG_TRANSACTION");
    }

    @Test
    void shouldBoundTotalNodesAndReportOmittedSpans() {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /loop", 8, 0, 3);

        for (int index = 0; index < 10; index++) {
            trace.addSpan("loop-" + index, SpanKind.SERVICE, "");
            trace.stopSpan();
        }
        Span detached = trace.addDetachedSpan("transaction", SpanKind.TRANSACTION, "write");
        trace.stopRoot();

        assertThat(detached).isNull();
        assertThat(trace.renderTree())
                .contains("loop-0", "loop-1", "TRACE_TRUNCATED omitted=9 maxNodes=3")
                .doesNotContain("loop-2", "transaction");
        assertThat(trace.incidentReasons()).containsExactly("TRACE_TRUNCATED");
    }

    @Test
    void shouldReleaseNodeBudgetAfterDroppingShortSpan() {
        TraceTree trace = new TraceTree("HTTP", SpanKind.HTTP, "GET /fast", 8, Long.MAX_VALUE, 2);

        trace.addSpan("first", SpanKind.SERVICE, "");
        trace.stopSpan();
        trace.addSpan("second", SpanKind.SERVICE, "");
        trace.stopSpan();
        trace.stopRoot();

        assertThat(trace.renderTree())
                .doesNotContain("first", "second", "TRACE_TRUNCATED");
        assertThat(trace.incidentReasons()).isEmpty();
    }

    @Test
    void shouldStopRenderingAtHardCharacterLimitAndAppendMarker() {
        TraceTree trace = new TraceTree("x".repeat(TraceTree.MAX_RENDER_CHARS * 2), SpanKind.HTTP, "", 8, 0);
        trace.addSpan("must-not-be-rendered-after-budget", SpanKind.SERVICE, "");
        trace.stopSpan();
        trace.stopRoot();

        String tree = trace.renderTree();

        assertThat(tree)
                .hasSize(TraceTree.MAX_RENDER_CHARS)
                .endsWith("[Trace] TRACE_OUTPUT_TRUNCATED maxChars=" + TraceTree.MAX_RENDER_CHARS)
                .doesNotContain("must-not-be-rendered-after-budget");
    }

    private static int occurrences(String text, String pattern) {
        return text.split(java.util.regex.Pattern.quote(pattern), -1).length - 1;
    }
}
