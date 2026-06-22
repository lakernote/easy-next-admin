package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.dto.WebLogFileSnapshot;
import com.laker.admin.module.monitor.service.WebLogFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerGroup;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author laker
 * @since 2021-08-05
 */
@Controller
@RequestMapping("/api/monitor/weblog")
@ConditionalOnProperty(prefix = "easy.features", name = "web-log", havingValue = "true", matchIfMissing = true)
public class WebLogController {
    private static final List<String> LOGGER_NAME_PREFIXES = List.of(
            "ROOT",
            "root",
            "com.laker.admin",
            "org.springframework",
            "org.hibernate",
            "com.zaxxer.hikari",
            "org.mybatis",
            "org.apache.ibatis"
    );

    @Autowired
    private LoggingSystem loggingSystem;
    @Autowired
    private LoggerGroups loggerGroups;
    @Autowired
    private WebLogFileService webLogFileService;

    @GetMapping("/file/snapshot")
    @ResponseBody
    @EasyPermission(EasyPermissions.Monitor.WEBLOG_VIEW)
    public Response<WebLogFileSnapshot> fileSnapshot(@RequestParam(required = false, defaultValue = "300") int lines,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String level) {
        return Response.ok(webLogFileService.snapshot(lines, keyword, level));
    }

    @PostMapping(value = "/level")
    @ResponseBody
    @EasyPermission(EasyPermissions.Monitor.WEBLOG_LEVEL)
    @EasyAudit(module = "运行监控", action = "调整日志级别")
    public Response<String> configureLogLevel(@RequestParam String name, @RequestParam LogLevel configuredLevel) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请输入日志名称");
        }
        if (configuredLevel == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择日志级别");
        }
        LoggerGroup group = this.loggerGroups.get(name);
        if (group != null && group.hasMembers()) {
            group.configureLogLevel(configuredLevel, this.loggingSystem::setLogLevel);
            return Response.ok("ok");
        }
        validateLoggerName(name);
        this.loggingSystem.setLogLevel(name, configuredLevel);
        return Response.ok("ok");
    }

    private void validateLoggerName(String name) {
        String trimmed = name.trim();
        boolean allowed = LOGGER_NAME_PREFIXES.stream().anyMatch(prefix ->
                trimmed.equals(prefix) || trimmed.startsWith(prefix + "."));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只允许调整系统白名单内的日志名称");
        }
    }

}
