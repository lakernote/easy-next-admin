package com.laker.admin.module.monitor.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.spi.AppenderAttachable;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.module.monitor.dto.WebLogFileSnapshot;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WebLogFileService {
    private static final int DEFAULT_LINES = 300;
    private static final int MAX_LINES = 2_000;
    private static final long MAX_READ_BYTES = 2 * 1024 * 1024;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private final EasyNextAdminConfig easyNextAdminConfig;

    public WebLogFileService(EasyNextAdminConfig easyNextAdminConfig) {
        this.easyNextAdminConfig = easyNextAdminConfig;
    }

    public WebLogFileSnapshot snapshot(int lines, String keyword, String level) {
        return snapshot(resolveActiveLogFile(), lines, keyword, level);
    }

    public Path resolveActiveLogFile() {
        return resolveLogbackFile()
                .orElseGet(() -> normalizePath(easyNextAdminConfig.getLogFilePath()));
    }

    WebLogFileSnapshot snapshot(Path logFile, int lines, String keyword, String level) {
        int requestedLines = normalizeLines(lines);
        Path normalizedLogFile = logFile.toAbsolutePath().normalize();
        long fileSizeBytes = safeSize(normalizedLogFile);
        String lastModifiedTime = safeLastModifiedTime(normalizedLogFile);
        String sampleTime = formatTime(LocalDateTime.now());
        if (!Files.isRegularFile(normalizedLogFile) || !Files.isReadable(normalizedLogFile)) {
            return WebLogFileSnapshot.builder()
                    .source("logback")
                    .fileName(fileName(normalizedLogFile))
                    .filePath(normalizedLogFile.toString())
                    .charset(StandardCharsets.UTF_8.name())
                    .fileSizeBytes(fileSizeBytes)
                    .lastModifiedTime(lastModifiedTime)
                    .sampleTime(sampleTime)
                    .requestedLines(requestedLines)
                    .returnedLines(0)
                    .truncated(false)
                    .readable(false)
                    .message("日志文件不存在或当前进程不可读")
                    .lines(List.of())
                    .build();
        }

        TailContent tailContent = readTail(normalizedLogFile, fileSizeBytes);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedLevel = normalizeLevel(level);
        List<String> filteredLines = tailContent.lines().stream()
                .filter(line -> matchesKeyword(line, normalizedKeyword))
                .filter(line -> matchesLevel(line, normalizedLevel))
                .collect(Collectors.toList());
        boolean lineTruncated = filteredLines.size() > requestedLines;
        List<String> selectedLines = lineTruncated
                ? filteredLines.subList(filteredLines.size() - requestedLines, filteredLines.size())
                : filteredLines;

        return WebLogFileSnapshot.builder()
                .source("logback")
                .fileName(fileName(normalizedLogFile))
                .filePath(normalizedLogFile.toString())
                .charset(StandardCharsets.UTF_8.name())
                .fileSizeBytes(fileSizeBytes)
                .lastModifiedTime(lastModifiedTime)
                .sampleTime(sampleTime)
                .requestedLines(requestedLines)
                .returnedLines(selectedLines.size())
                .truncated(tailContent.truncated() || lineTruncated)
                .readable(true)
                .message(tailContent.truncated() ? "已读取日志文件尾部内容" : "已读取当前日志文件")
                .lines(List.copyOf(selectedLines))
                .build();
    }

    private Optional<Path> resolveLogbackFile() {
        try {
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
                return Optional.empty();
            }
            Set<Appender<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Logger logger : loggerContext.getLoggerList()) {
                Iterator<Appender<ILoggingEvent>> iterator = logger.iteratorForAppenders();
                while (iterator.hasNext()) {
                    Optional<Path> filePath = filePathFromAppender(iterator.next(), visited);
                    if (filePath.isPresent()) {
                        return filePath;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Path> filePathFromAppender(Appender<?> appender, Set<Appender<?>> visited) {
        if (appender == null || !visited.add(appender)) {
            return Optional.empty();
        }
        if (appender instanceof FileAppender<?> fileAppender && StringUtils.hasText(fileAppender.getFile())) {
            return Optional.of(normalizePath(fileAppender.getFile()));
        }
        if (appender instanceof AppenderAttachable<?> appenderAttachable) {
            Iterator<?> iterator = appenderAttachable.iteratorForAppenders();
            while (iterator.hasNext()) {
                Object nested = iterator.next();
                if (nested instanceof Appender<?> nestedAppender) {
                    Optional<Path> filePath = filePathFromAppender(nestedAppender, visited);
                    if (filePath.isPresent()) {
                        return filePath;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private TailContent readTail(Path logFile, long fileSizeBytes) {
        if (fileSizeBytes <= 0) {
            return new TailContent(List.of(), false);
        }
        long position = Math.max(0, fileSizeBytes - MAX_READ_BYTES);
        int bytesToRead = (int) (fileSizeBytes - position);
        ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
        try (SeekableByteChannel channel = Files.newByteChannel(logFile, StandardOpenOption.READ)) {
            channel.position(position);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // Continue until the tail buffer is full or EOF is reached.
            }
        } catch (IOException e) {
            return new TailContent(List.of(), false);
        }
        buffer.flip();
        String content = StandardCharsets.UTF_8.decode(buffer).toString();
        boolean truncated = position > 0;
        if (truncated) {
            int firstLineBreak = content.indexOf('\n');
            if (firstLineBreak >= 0 && firstLineBreak + 1 < content.length()) {
                content = content.substring(firstLineBreak + 1);
            }
        }
        return new TailContent(content.lines().toList(), truncated);
    }

    private int normalizeLines(int lines) {
        if (lines <= 0) {
            return DEFAULT_LINES;
        }
        return Math.min(lines, MAX_LINES);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeLevel(String level) {
        String normalized = StringUtils.hasText(level) ? level.trim().toUpperCase(Locale.ROOT) : "";
        return LEVELS.contains(normalized) ? normalized : "";
    }

    private boolean matchesKeyword(String line, String keyword) {
        return !StringUtils.hasText(keyword) || line.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean matchesLevel(String line, String level) {
        return !StringUtils.hasText(level) || line.contains(" " + level) || line.contains(level + " ");
    }

    private Path normalizePath(String filePath) {
        return Path.of(filePath).toAbsolutePath().normalize();
    }

    private long safeSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private String safeLastModifiedTime(Path path) {
        try {
            return Files.exists(path) ? formatTime(Files.getLastModifiedTime(path)) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private String fileName(Path path) {
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }

    private String formatTime(FileTime fileTime) {
        return formatTime(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault()));
    }

    private String formatTime(LocalDateTime localDateTime) {
        return TIME_FORMATTER.format(localDateTime);
    }

    private record TailContent(List<String> lines, boolean truncated) {
    }
}
