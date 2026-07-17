package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

public interface AssistantToolArgumentValidator {
    AssistantToolArgumentResolution validate(AssistantRequestContext context,
                                             AssistantToolSelection toolSelection,
                                             AssistantToolArgumentResolution resolution);
}
