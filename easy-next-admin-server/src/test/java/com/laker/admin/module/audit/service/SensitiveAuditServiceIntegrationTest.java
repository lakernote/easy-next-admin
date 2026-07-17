package com.laker.admin.module.audit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.audit.controller.AuditDataChangeLogController;
import com.laker.admin.module.audit.dto.AuditDataChangeLogView;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SensitiveAuditServiceIntegrationTest {

    @Autowired
    private SensitiveAuditService sensitiveAuditService;
    @Autowired
    private IAuditDataChangeLogService dataChangeLogService;
    @Autowired
    private AuditDataChangeLogController dataChangeLogController;

    @BeforeEach
    void setPrincipal() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .nickName("超级管理员")
                .superAdmin(true)
                .build());
    }

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldWriteSensitiveDataChangeAuditLog() {
        sensitiveAuditService.record("菜单管理", "发布菜单资源", "MENU", "202604280104000001", "{\"title\":\"工作台\"}");

        AuditDataChangeLog log = dataChangeLogService.getOne(Wrappers.<AuditDataChangeLog>lambdaQuery()
                .eq(AuditDataChangeLog::getBizType, "MENU")
                .eq(AuditDataChangeLog::getBizId, "202604280104000001")
                .orderByDesc(AuditDataChangeLog::getCreatedAt)
                .last("limit 1"));

        assertThat(log).isNotNull();
        assertThat(log.getOperatorId()).isEqualTo(202604280101000001L);
        assertThat(log.getTableName()).isEqualTo("菜单管理");
        assertThat(log.getChangeType()).isEqualTo("UPDATE");
        assertThat(log.getChangedFields()).isEqualTo("发布菜单资源");
        assertThat(log.getAfterJson()).contains("MENU", "202604280104000001", "工作台");
    }

    @Test
    void shouldPageDataChangeLogsWithoutOperator() {
        AuditDataChangeLog log = new AuditDataChangeLog();
        log.setBizType("SYSTEM");
        log.setBizId("unknown-operator");
        log.setTableName("系统配置");
        log.setChangeType("UPDATE");
        log.setChangedFields("自动任务写入");
        log.setAfterJson("{}");
        log.setCreatedAt(LocalDateTime.now());
        dataChangeLogService.save(log);

        assertThat(dataChangeLogController.pageAll(1, 10, "unknown-operator", null).getData().list())
                .extracting(AuditDataChangeLogView::getOperator)
                .containsNull();
    }
}
