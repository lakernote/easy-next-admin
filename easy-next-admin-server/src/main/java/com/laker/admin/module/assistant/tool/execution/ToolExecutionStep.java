package com.laker.admin.module.assistant.tool.execution;

import java.util.List;

/** Tool Call 在企业执行边界内的可观测步骤。 */
public record ToolExecutionStep(
        int sequence,
        ToolExecutionStepType type,
        ToolExecutionStepStatus status,
        String toolName,
        String description,
        List<String> inputKeys,
        List<String> outputKeys,
        String observation
) {
    public ToolExecutionStep {
        status = status == null ? ToolExecutionStepStatus.PENDING : status;
        inputKeys = inputKeys == null ? List.of() : List.copyOf(inputKeys);
        outputKeys = outputKeys == null ? List.of() : List.copyOf(outputKeys);
        observation = observation == null ? "" : observation;
    }

    public ToolExecutionStep withStatus(ToolExecutionStepStatus nextStatus, String nextObservation) {
        return new ToolExecutionStep(
                sequence,
                type,
                nextStatus,
                toolName,
                description,
                inputKeys,
                outputKeys,
                nextObservation
        );
    }
}
