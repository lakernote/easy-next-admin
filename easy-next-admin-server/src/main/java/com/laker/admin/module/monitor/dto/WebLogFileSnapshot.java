package com.laker.admin.module.monitor.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WebLogFileSnapshot {
    private String source;
    private String fileName;
    private String filePath;
    private String charset;
    private long fileSizeBytes;
    private String lastModifiedTime;
    private String sampleTime;
    private int requestedLines;
    private int returnedLines;
    private boolean truncated;
    private boolean readable;
    private String message;
    private List<String> lines;
}
