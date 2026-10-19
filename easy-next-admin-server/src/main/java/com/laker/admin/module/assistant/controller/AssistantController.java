package com.laker.admin.module.assistant.controller;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import io.github.lakernote.observability.trace.TraceIdContext;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.config.AssistantRuntimeConfiguration;
import com.laker.admin.module.assistant.config.AssistantRuntimeProperties;
import com.laker.admin.module.assistant.dto.AssistantChatStreamEvent;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantMessageRequest;
import com.laker.admin.module.assistant.stream.AssistantChatSseEventSink;
import com.laker.admin.module.assistant.stream.AssistantUserStreamEventAdapter;
import com.laker.admin.module.assistant.turn.AgentTurnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "智能助手")
@RestController
@RequestMapping("/api/assistant")
@Slf4j
public class AssistantController {
    private final AgentTurnService agentTurnService;
    private final TaskExecutor streamExecutor;
    private final TaskScheduler heartbeatScheduler;
    private final AssistantRuntimeProperties runtimeProperties;
    private final OperationObservation operationObservation;

    public AssistantController(
            AgentTurnService agentTurnService,
            @Qualifier(AssistantRuntimeConfiguration.STREAM_EXECUTOR_BEAN) TaskExecutor streamExecutor,
            @Qualifier(AssistantRuntimeConfiguration.HEARTBEAT_SCHEDULER_BEAN) TaskScheduler heartbeatScheduler,
            AssistantRuntimeProperties runtimeProperties,
            OperationObservation operationObservation) {
        this.agentTurnService = agentTurnService;
        this.streamExecutor = streamExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.runtimeProperties = runtimeProperties;
        this.operationObservation = operationObservation;
    }

    @PostMapping("/messages")
    @EasyPermission(EasyPermissions.Assistant.DEBUG)
    @EasyAudit(module = "智能助手", action = "运行 Agent 同步调试")
    @Operation(summary = "同步运行 Agent 并返回完整调试流程")
    public Response<AssistantEntryResponse> runDebugTurn(@Valid @RequestBody AssistantMessageRequest request) {
        return Response.ok(agentTurnService.run(request));
    }

    @PostMapping(value = "/chat/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @EasyPermission(EasyPermissions.Assistant.CHAT)
    @EasyAudit(module = "智能助手", action = "发送用户流式对话")
    @Operation(summary = "发送用户消息并通过 SSE 返回安全进度和最终回复")
    public SseEmitter streamUserChat(@Valid @RequestBody AssistantMessageRequest request) {
        SseEmitter emitter = new SseEmitter(runtimeProperties.getStreamTimeout().toMillis());
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        String traceId = TraceIdContext.getOrCreateTraceId();
        OperationObservation.ContextSnapshot observationContext = operationObservation.captureContext();
        AssistantChatSseEventSink eventSink = new AssistantChatSseEventSink(emitter, traceId);
        AssistantUserStreamEventAdapter userEventAdapter = new AssistantUserStreamEventAdapter(eventSink);
        streamExecutor.execute(() -> {
            TraceIdContext.putTraceId(traceId);
            EasySecurityContext.setPrincipal(principal);
            try {
                try (OperationObservation.ContextBinding ignored = observationContext.bind()) {
                    agentTurnService.run(request, userEventAdapter);
                }
                // AgentTurnService 已通过安全适配器发送 done 事件；这里只负责关闭连接。
                // 先释放 worker 监控上下文，再触发 Servlet 异步完成分派，避免两个线程同时绑定根操作。
                eventSink.complete();
            } catch (RuntimeException ex) {
                log.warn("Assistant user stream execution failed, traceId={}, failureType={}",
                        traceId, ex.getClass().getSimpleName());
                String failureMessage = ex instanceof BusinessException
                        ? ex.getMessage()
                        : "助手执行失败，请稍后重试";
                eventSink.fail(AssistantChatStreamEvent.failed(failureMessage));
            } finally {
                // 异步线程会复用，必须清理 ThreadLocal 上下文
                EasySecurityContext.clear();
                TraceIdContext.clear();
            }
        });
        eventSink.startHeartbeat(heartbeatScheduler, runtimeProperties.getHeartbeatInterval());
        return emitter;
    }
}
