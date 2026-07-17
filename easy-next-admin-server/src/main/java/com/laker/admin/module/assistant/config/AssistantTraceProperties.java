package com.laker.admin.module.assistant.config;

import com.laker.admin.module.assistant.trace.AssistantTraceContentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "easy.assistant.trace")
@Validated
public class AssistantTraceProperties {
    @NotNull
    private AssistantTraceContentMode contentMode = AssistantTraceContentMode.METADATA_ONLY;

    @Min(1_000)
    @Max(100_000)
    private int maxPayloadLength = 16_000;

    public AssistantTraceContentMode getContentMode() {
        return contentMode;
    }

    public void setContentMode(AssistantTraceContentMode contentMode) {
        this.contentMode = contentMode;
    }

    public int getMaxPayloadLength() {
        return maxPayloadLength;
    }

    public void setMaxPayloadLength(int maxPayloadLength) {
        this.maxPayloadLength = maxPayloadLength;
    }
}
