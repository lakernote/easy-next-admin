package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.module.system.service.ISysUserRoleService;
import com.laker.admin.module.system.service.impl.SysUserRoleServiceImpl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomaticServiceTracingAspectTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void shouldAddPublicServiceMethodToAnExistingTrace() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(config);
        PlainService target = new PlainService();
        ProceedingJoinPoint joinPoint = joinPoint(target, "loadUser", "loaded");
        TraceContext.startRoot("GET /api/users/1", SpanType.Http, "", 8, 0);

        Object result = aspect.traceServiceCall(joinPoint);

        assertThat(result).isEqualTo("loaded");
        assertThat(finishTrace()).contains("[Service] PlainService#loadUser");
    }

    @Test
    void shouldLeaveExplicitTraceAnnotationToEasyTracingAspect() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(config);
        ExplicitlyTracedService target = new ExplicitlyTracedService();
        ProceedingJoinPoint joinPoint = joinPoint(target, "loadUser", "loaded");
        TraceContext.startRoot("GET /api/users/1", SpanType.Http, "", 8, 0);

        aspect.traceServiceCall(joinPoint);

        assertThat(finishTrace()).doesNotContain("ExplicitlyTracedService#loadUser");
    }

    @Test
    void shouldDetectExplicitTraceAnnotationDeclaredOnAServiceInterface() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(config);
        InterfaceTracedService target = new InterfaceTracedService();
        ProceedingJoinPoint joinPoint = joinPoint(
                target,
                ExplicitlyTracedContract.class.getMethod("loadUser"),
                "loaded");
        TraceContext.startRoot("GET /api/users/1", SpanType.Http, "", 8, 0);

        aspect.traceServiceCall(joinPoint);

        assertThat(finishTrace()).doesNotContain("InterfaceTracedService#loadUser");
    }

    @Test
    void shouldRespectMethodLevelIgnoreAnnotation() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(config);
        IgnoredService target = new IgnoredService();
        ProceedingJoinPoint joinPoint = joinPoint(target, "loadUser", "loaded");
        TraceContext.startRoot("GET /api/users/1", SpanType.Http, "", 8, 0);

        aspect.traceServiceCall(joinPoint);

        assertThat(finishTrace()).doesNotContain("IgnoredService#loadUser");
    }

    @Test
    void shouldProceedWithoutSpanWhenThereIsNoEntryTrace() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(config);
        PlainService target = new PlainService();
        ProceedingJoinPoint joinPoint = joinPoint(target, "loadUser", "loaded");

        assertThat(aspect.traceServiceCall(joinPoint)).isEqualTo("loaded");
        verify(joinPoint).proceed();
        assertThat(TraceContext.active()).isFalse();
    }

    @Test
    void monitoringSetupErrorMustNotPreventTheServiceCall() throws Throwable {
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(new EasyNextAdminConfig());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenThrow(new AssertionError("trace metadata unavailable"));
        when(joinPoint.proceed()).thenReturn("loaded");
        TraceContext.startRoot("GET /api/users/1", SpanType.Http, "", 8, 0);

        assertThat(aspect.traceServiceCall(joinPoint)).isEqualTo("loaded");

        verify(joinPoint).proceed();
        assertThat(TraceContext.active()).isFalse();
    }

    @Test
    void pointcutShouldTraceInterfaceBasedSpringServiceProxy() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        AutomaticServiceTracingAspect aspect = new AutomaticServiceTracingAspect(config);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new SysUserRoleServiceImpl());
        proxyFactory.setInterfaces(ISysUserRoleService.class);
        proxyFactory.setProxyTargetClass(false);
        proxyFactory.addAspect(aspect);
        ISysUserRoleService proxy = proxyFactory.getProxy();
        TraceContext.startRoot("GET /api/users/1", SpanType.Http, "", 8, 0);

        assertThat(proxy.listRoleIdsByUserId(null)).isEmpty();

        assertThat(finishTrace()).contains("[Service] SysUserRoleServiceImpl#listRoleIdsByUserId");
    }

    private static ProceedingJoinPoint joinPoint(Object target, String methodName, Object result) throws Throwable {
        return joinPoint(target, target.getClass().getMethod(methodName), result);
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

    private static String finishTrace() {
        AtomicReference<TraceContext.DeferredCompletion> completion = new AtomicReference<>();
        TraceContext.stopRootDeferred(null, completion::set);
        return completion.get().tree();
    }

    @Service
    static class PlainService {
        public String loadUser() {
            return "loaded";
        }
    }

    @Service
    @EasyTrace(spanType = SpanType.Service)
    static class ExplicitlyTracedService {
        public String loadUser() {
            return "loaded";
        }
    }

    @Service
    static class IgnoredService {
        @EasyIgnoreTrace
        public String loadUser() {
            return "loaded";
        }
    }

    @EasyTrace(spanType = SpanType.Service)
    interface ExplicitlyTracedContract {
        String loadUser();
    }

    @Service
    static class InterfaceTracedService implements ExplicitlyTracedContract {
        @Override
        public String loadUser() {
            return "loaded";
        }
    }
}
