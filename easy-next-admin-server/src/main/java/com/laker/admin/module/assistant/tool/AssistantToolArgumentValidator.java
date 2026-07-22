package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

import java.util.List;

public interface AssistantToolArgumentValidator {
    AssistantToolArgumentResolution validate(AssistantRequestContext context,
                                             AssistantToolSelection toolSelection,
                                             AssistantToolArgumentResolution resolution);

    /**
     * 使用本轮已经成功执行的 Tool Result 作为受控参数来源。
     *
     * <p>默认实现保持原有行为；只有明确声明来源规则的业务校验器才能接受这些结果，不能把任意
     * observation 自动提升为写参数。</p>
     */
    default AssistantToolArgumentResolution validate(AssistantRequestContext context,
                                                     AssistantToolSelection toolSelection,
                                                     AssistantToolArgumentResolution resolution,
                                                     List<AssistantToolResult> trustedToolResults) {
        return validate(context, toolSelection, resolution);
    }
}
