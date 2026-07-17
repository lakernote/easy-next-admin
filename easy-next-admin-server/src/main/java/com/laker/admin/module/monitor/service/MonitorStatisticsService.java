package com.laker.admin.module.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import com.laker.admin.infrastructure.observability.remote.mapper.RemoteCallLogMapper;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.module.audit.entity.AuditApiLog;
import com.laker.admin.module.audit.mapper.AuditApiLogMapper;
import com.laker.admin.module.monitor.dto.MonitorStatisticsOverview;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.mapper.ScheduleJobLogMapper;
import com.laker.admin.module.schedule.mapper.ScheduleJobMapper;
import com.laker.admin.module.system.dto.OnlineSessionView;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 监控统计查询服务。
 *
 * <p>不维护第二套统计表，优先从业务真实日志和运行态指标聚合，避免后台页面和后端事实脱节。</p>
 */
@Service
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MonitorStatisticsService {
    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int DEFAULT_SLOW_REQUEST_MS = 1000;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditApiLogMapper auditApiLogMapper;
    private final RemoteCallLogMapper remoteCallLogMapper;
    private final ScheduleJobMapper scheduleJobMapper;
    private final ScheduleJobLogMapper scheduleJobLogMapper;
    private final EasyAuthService authService;
    private final MeterRegistry meterRegistry;

    public MonitorStatisticsService(AuditApiLogMapper auditApiLogMapper,
                                    RemoteCallLogMapper remoteCallLogMapper,
                                    ScheduleJobMapper scheduleJobMapper,
                                    ScheduleJobLogMapper scheduleJobLogMapper,
                                    EasyAuthService authService,
                                    MeterRegistry meterRegistry) {
        this.auditApiLogMapper = auditApiLogMapper;
        this.remoteCallLogMapper = remoteCallLogMapper;
        this.scheduleJobMapper = scheduleJobMapper;
        this.scheduleJobLogMapper = scheduleJobLogMapper;
        this.authService = authService;
        this.meterRegistry = meterRegistry;
    }

    public MonitorStatisticsOverview overview() {
        return MonitorStatisticsOverview.builder()
                .api(apiStatistics(DEFAULT_DAYS, DEFAULT_TOP_LIMIT))
                .onlineUsers(onlineUserStatistics(1, 5))
                .remoteCalls(remoteCallStatistics())
                .jobs(jobStatistics(DEFAULT_TOP_LIMIT))
                .build();
    }

    public MonitorStatisticsOverview.ApiStatistics apiStatistics(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays(days));
        Map<String, Object> summary = oneAuditMap(new QueryWrapper<AuditApiLog>()
                .select(
                        "COUNT(*) AS total_count",
                        "SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS failure_count",
                        "SUM(CASE WHEN cost >= " + DEFAULT_SLOW_REQUEST_MS + " THEN 1 ELSE 0 END) AS slow_count",
                        "COUNT(DISTINCT ip) AS unique_ip_count",
                        "COALESCE(AVG(cost), 0) AS avg_cost_ms",
                        "COALESCE(MAX(cost), 0) AS max_cost_ms")
                .eq("deleted", 0)
                .ge("create_time", since));
        long totalCount = longValue(summary, "total_count");
        long failureCount = longValue(summary, "failure_count");
        long successCount = Math.max(totalCount - failureCount, 0);
        return MonitorStatisticsOverview.ApiStatistics.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failureCount(failureCount)
                .uniqueIpCount(longValue(summary, "unique_ip_count"))
                .slowCount(intValue(summary, "slow_count"))
                .avgCostMs(round(doubleValue(summary, "avg_cost_ms")))
                .maxCostMs(intValue(summary, "max_cost_ms"))
                .successRate(rate(successCount, totalCount))
                .endpoints(apiEndpointStatistics(since, limit))
                .build();
    }

    public PageResponse<OnlineSessionView> onlineUsers(int page, int pageSize) {
        return authService.pageOnlineSessions(Math.max(page, 1), safeLimit(pageSize, 100));
    }

    public List<MonitorStatisticsOverview.RemoteCallStatistics> remoteCallStatistics() {
        List<MonitorStatisticsOverview.RemoteCallStatistics> persisted = persistedRemoteCallStatistics();
        if (!persisted.isEmpty()) {
            return persisted;
        }
        Map<String, RemoteCallAccumulator> accumulators = new HashMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            if (!(meter instanceof Timer timer) || !"easy.remote.calls".equals(meter.getId().getName())) {
                continue;
            }
            String target = textOrDash(tagValue(meter.getId(), "target"));
            String method = textOrDash(tagValue(meter.getId(), "method"));
            String result = tagValue(meter.getId(), "result");
            String key = target + "#" + method;
            accumulators.computeIfAbsent(key, unused -> new RemoteCallAccumulator(target, method))
                    .record(timer, result);
        }
        return accumulators.values().stream()
                .map(RemoteCallAccumulator::toStatistics)
                .sorted(Comparator.comparing(MonitorStatisticsOverview.RemoteCallStatistics::getTotalCount).reversed()
                        .thenComparing(MonitorStatisticsOverview.RemoteCallStatistics::getTarget)
                        .thenComparing(MonitorStatisticsOverview.RemoteCallStatistics::getMethod))
                .toList();
    }

    private List<MonitorStatisticsOverview.RemoteCallStatistics> persistedRemoteCallStatistics() {
        LocalDateTime since = LocalDateTime.now().minusDays(DEFAULT_DAYS);
        try {
            List<Map<String, Object>> rows = remoteCallLogMapper.selectMaps(new QueryWrapper<RemoteCallLog>()
                    .select(
                            "COALESCE(target, '-') AS target",
                            "COALESCE(method, '-') AS method",
                            "COUNT(*) AS total_count",
                            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failure_count",
                            "COALESCE(AVG(duration_ms), 0) AS avg_cost_ms",
                            "COALESCE(MAX(duration_ms), 0) AS max_cost_ms")
                    .eq("deleted", 0)
                    .ge("create_time", since)
                    .groupBy("target", "method")
                    .orderByDesc("total_count")
                    .last("LIMIT " + DEFAULT_TOP_LIMIT));
            return rows.stream().map(row -> {
                long totalCount = longValue(row, "total_count");
                long failureCount = longValue(row, "failure_count");
                long successCount = Math.max(totalCount - failureCount, 0);
                return MonitorStatisticsOverview.RemoteCallStatistics.builder()
                        .target(textOrDash(stringValue(row, "target")))
                        .method(textOrDash(stringValue(row, "method")))
                        .totalCount(totalCount)
                        .successCount(successCount)
                        .failureCount(failureCount)
                        .avgCostMs(round(doubleValue(row, "avg_cost_ms")))
                        .maxCostMs(round(doubleValue(row, "max_cost_ms")))
                        .successRate(rate(successCount, totalCount))
                        .build();
            }).toList();
        } catch (RuntimeException ex) {
            // 老数据库未补建远程调用日志表时，页面仍可展示当前 JVM 内的 Micrometer 统计。
            log.debug("读取远程调用持久化统计失败，退回 Micrometer 运行态统计", ex);
            return List.of();
        }
    }

    public MonitorStatisticsOverview.JobStatistics jobStatistics(int limit) {
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        Map<String, Object> summary = oneJobLogMap(new QueryWrapper<ScheduleJobLog>()
                .select(
                        "COUNT(*) AS total_count",
                        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS failure_count",
                        "COALESCE(AVG(cost), 0) AS avg_cost_ms",
                        "COALESCE(MAX(cost), 0) AS max_cost_ms")
                .eq("deleted", 0)
                .ge("start_time", since24h));
        long executionCount = longValue(summary, "total_count");
        long failureCount = longValue(summary, "failure_count");
        long successCount = Math.max(executionCount - failureCount, 0);

        return MonitorStatisticsOverview.JobStatistics.builder()
                .totalJobs(scheduleJobMapper.selectCount(new QueryWrapper<ScheduleJob>().eq("deleted", 0)))
                .enabledJobs(scheduleJobMapper.selectCount(new QueryWrapper<ScheduleJob>().eq("deleted", 0).eq("enable", true)))
                .executionCount24h(executionCount)
                .failureCount24h(failureCount)
                .avgCostMs24h(round(doubleValue(summary, "avg_cost_ms")))
                .maxCostMs24h(intValue(summary, "max_cost_ms"))
                .successRate24h(rate(successCount, executionCount))
                .executions(jobExecutionStatistics(limit))
                .build();
    }

    private List<MonitorStatisticsOverview.ApiEndpointStatistics> apiEndpointStatistics(LocalDateTime since, int limit) {
        int safeLimit = safeLimit(limit, 50);
        List<Map<String, Object>> rows = auditApiLogMapper.selectMaps(new QueryWrapper<AuditApiLog>()
                .select(
                        "COALESCE(uri, '-') AS uri",
                        "COALESCE(method, '-') AS method",
                        "COUNT(*) AS total_count",
                        "SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS failure_count",
                        "COALESCE(AVG(cost), 0) AS avg_cost_ms",
                        "COALESCE(MAX(cost), 0) AS max_cost_ms",
                        "MAX(create_time) AS last_time")
                .eq("deleted", 0)
                .ge("create_time", since)
                .groupBy("uri", "method")
                .orderByDesc("total_count")
                .last("LIMIT " + safeLimit));
        return rows.stream().map(row -> {
            long totalCount = longValue(row, "total_count");
            long failureCount = longValue(row, "failure_count");
            long successCount = Math.max(totalCount - failureCount, 0);
            return MonitorStatisticsOverview.ApiEndpointStatistics.builder()
                    .uri(textOrDash(stringValue(row, "uri")))
                    .method(textOrDash(stringValue(row, "method")))
                    .totalCount(totalCount)
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .avgCostMs(round(doubleValue(row, "avg_cost_ms")))
                    .maxCostMs(intValue(row, "max_cost_ms"))
                    .successRate(rate(successCount, totalCount))
                    .lastTime(localDateTimeValue(row, "last_time"))
                    .build();
        }).toList();
    }

    private MonitorStatisticsOverview.OnlineUserStatistics onlineUserStatistics(int page, int pageSize) {
        PageResponse<OnlineSessionView> onlinePage = onlineUsers(page, pageSize);
        return MonitorStatisticsOverview.OnlineUserStatistics.builder()
                .totalCount(onlinePage.getData().total())
                .records(onlinePage.getData().list())
                .build();
    }

    private List<MonitorStatisticsOverview.JobExecutionStatistics> jobExecutionStatistics(int limit) {
        int safeLimit = safeLimit(limit, 50);
        LocalDateTime since = LocalDateTime.now().minusDays(DEFAULT_DAYS);
        List<Map<String, Object>> rows = scheduleJobLogMapper.selectMaps(new QueryWrapper<ScheduleJobLog>()
                .select(
                        "COALESCE(job_code, '-') AS job_code",
                        "COUNT(*) AS total_count",
                        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS failure_count",
                        "COALESCE(AVG(cost), 0) AS avg_cost_ms",
                        "COALESCE(MAX(cost), 0) AS max_cost_ms",
                        "MAX(start_time) AS last_start_time")
                .eq("deleted", 0)
                .ge("start_time", since)
                .groupBy("job_code")
                .orderByDesc("total_count")
                .last("LIMIT " + safeLimit));
        Map<String, ScheduleJobLog> latestLogs = latestJobLogs(since);
        return rows.stream().map(row -> {
            String jobCode = textOrDash(stringValue(row, "job_code"));
            long totalCount = longValue(row, "total_count");
            long failureCount = longValue(row, "failure_count");
            long successCount = Math.max(totalCount - failureCount, 0);
            ScheduleJobLog latestLog = latestLogs.get(jobCode);
            return MonitorStatisticsOverview.JobExecutionStatistics.builder()
                    .jobCode(jobCode)
                    .totalCount(totalCount)
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .avgCostMs(round(doubleValue(row, "avg_cost_ms")))
                    .maxCostMs(intValue(row, "max_cost_ms"))
                    .lastStatus(latestLog == null ? null : latestLog.getStatus())
                    .lastStartTime(latestLog == null ? localDateTimeValue(row, "last_start_time") : latestLog.getStartTime())
                    .build();
        }).toList();
    }

    private Map<String, ScheduleJobLog> latestJobLogs(LocalDateTime since) {
        LambdaQueryWrapper<ScheduleJobLog> wrapper = new LambdaQueryWrapper<ScheduleJobLog>()
                .ge(ScheduleJobLog::getStartTime, since)
                .orderByDesc(ScheduleJobLog::getStartTime)
                .last("LIMIT 200");
        Map<String, ScheduleJobLog> latestLogs = new HashMap<>();
        for (ScheduleJobLog log : scheduleJobLogMapper.selectList(wrapper)) {
            latestLogs.putIfAbsent(textOrDash(log.getJobCode()), log);
        }
        return latestLogs;
    }

    private Map<String, Object> oneAuditMap(QueryWrapper<AuditApiLog> wrapper) {
        List<Map<String, Object>> rows = auditApiLogMapper.selectMaps(wrapper);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> oneJobLogMap(QueryWrapper<ScheduleJobLog> wrapper) {
        List<Map<String, Object>> rows = scheduleJobLogMapper.selectMaps(wrapper);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private int safeDays(int days) {
        return Math.max(1, Math.min(days, 30));
    }

    private int safeLimit(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }

    private int rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    private Double round(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private String textOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String tagValue(Meter.Id id, String key) {
        return id.getTags().stream()
                .filter(tag -> key.equals(tag.getKey()))
                .map(tag -> tag.getValue())
                .findFirst()
                .orElse("-");
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Map<String, Object> row, String key) {
        return Math.toIntExact(longValue(row, key));
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.longValue();
        }
        if (value instanceof CharSequence text && StringUtils.hasText(text)) {
            return Long.parseLong(text.toString());
        }
        return 0L;
    }

    private double doubleValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.doubleValue();
        }
        if (value instanceof CharSequence text && StringUtils.hasText(text)) {
            return Double.parseDouble(text.toString());
        }
        return 0D;
    }

    private LocalDateTime localDateTimeValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof CharSequence text && StringUtils.hasText(text)) {
            try {
                return LocalDateTime.parse(text.toString().replace('T', ' '), DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object mapValue(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        String camelKey = toCamelCase(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(camelKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String toCamelCase(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char item : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (item == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(item) : item);
            upperNext = false;
        }
        return builder.toString();
    }

    private static class RemoteCallAccumulator {
        private final String target;
        private final String method;
        private long totalCount;
        private long successCount;
        private long failureCount;
        private double totalCostMs;
        private double maxCostMs;

        private RemoteCallAccumulator(String target, String method) {
            this.target = target;
            this.method = method;
        }

        private void record(Timer timer, String result) {
            long count = timer.count();
            totalCount += count;
            if (Objects.equals(result, "failure")) {
                failureCount += count;
            } else {
                successCount += count;
            }
            totalCostMs += timer.totalTime(TimeUnit.MILLISECONDS);
            maxCostMs = Math.max(maxCostMs, timer.max(TimeUnit.MILLISECONDS));
        }

        private MonitorStatisticsOverview.RemoteCallStatistics toStatistics() {
            return MonitorStatisticsOverview.RemoteCallStatistics.builder()
                    .target(target)
                    .method(method)
                    .totalCount(totalCount)
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .avgCostMs(totalCount == 0 ? 0D : Math.round(totalCostMs * 100.0 / totalCount) / 100.0)
                    .maxCostMs(Math.round(maxCostMs * 100.0) / 100.0)
                    .successRate(totalCount == 0 ? 0 : (int) Math.round(successCount * 100.0 / totalCount))
                    .build();
        }
    }
}
