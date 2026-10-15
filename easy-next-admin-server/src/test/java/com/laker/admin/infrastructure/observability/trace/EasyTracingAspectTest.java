package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EasyTracingAspectTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void shouldReuseResolvedDescriptorAndKeepConfiguredMetadata() throws Throwable {
        EasyTracingAspect aspect = new EasyTracingAspect(new EasyNextAdminConfig());
        NamedService target = new NamedService();
        Method method = target.getClass().getMethod("load");

        assertThat(traceOnce(aspect, joinPoint(target, method, "first")))
                .contains("[Remote] user-directory tag=\"primary\"");
        assertThat(traceOnce(aspect, joinPoint(target, method, "second")))
                .contains("[Remote] user-directory tag=\"primary\"");
    }

    @Test
    void shouldResolveTraceMetadataDeclaredOnInterface() throws Throwable {
        EasyTracingAspect aspect = new EasyTracingAspect(new EasyNextAdminConfig());
        InterfaceService target = new InterfaceService();
        Method signatureMethod = TracedContract.class.getMethod("load");

        String tree = traceOnce(aspect, joinPoint(target, signatureMethod, "loaded"));

        assertThat(tree).contains("[Service] contract-load tag=\"interface\"");
    }

    @Test
    void monitoringSetupErrorMustNotPreventTheAnnotatedCall() throws Throwable {
        EasyTracingAspect aspect = new EasyTracingAspect(new EasyNextAdminConfig());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenThrow(new AssertionError("trace metadata unavailable"));
        when(joinPoint.proceed()).thenReturn("loaded");
        TraceContext.startRoot("GET /api/users", SpanType.Http, "", 8, 0);

        assertThat(aspect.around(joinPoint)).isEqualTo("loaded");

        verify(joinPoint).proceed();
        assertThat(TraceContext.active()).isFalse();
    }

    private static String traceOnce(EasyTracingAspect aspect, ProceedingJoinPoint joinPoint) throws Throwable {
        TraceContext.startRoot("GET /api/users", SpanType.Http, "", 8, 0);
        aspect.around(joinPoint);
        AtomicReference<TraceContext.DeferredCompletion> completion = new AtomicReference<>();
        TraceContext.stopRootDeferred(null, completion::set);
        return completion.get().tree();
    }

    private static ProceedingJoinPoint joinPoint(Object target, Method method, Object result) throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    @EasyTrace(value = "user-directory", spanType = SpanType.Remote, tag = "primary")
    static class NamedService {
        public String load() {
            return "loaded";
        }
    }

    @EasyTrace(value = "contract-load", spanType = SpanType.Service, tag = "interface")
    interface TracedContract {
        String load();
    }

    static class InterfaceService implements TracedContract {
        @Override
        public String load() {
            return "loaded";
        }
    }
}
