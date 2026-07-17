package com.laker.admin.module.workflow.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSeedSqlTest {
    @Test
    void mysqlSeedWorkflowInstancesShouldContainBusinessVariables() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V1__init.sql"));

        assertThat(sql)
                .contains("INSERT INTO `wf_ru_process_instance`")
                .contains("`variables_json`")
                .contains("JSON_OBJECT('leaveType', 'ANNUAL'")
                .contains("JSON_OBJECT('expenseType', 'TRAVEL'")
                .contains("JSON_OBJECT('repairRequestId', 202604280109000003")
                .contains("INSERT INTO `wf_hi_process_instance`")
                .contains("JSON_OBJECT('itemName', '研发测试设备'")
                .contains("INSERT INTO `wf_ru_cc`")
                .contains("(202604280108050001, 202604280108030004, 'start', '发起抄送', 202604280101000017, 0, NULL, NOW())")
                .contains("'WORKFLOW_CC', 'INFO', 'WORKFLOW_CC', '202604280108050001'");

        String definitionSeed = sql.substring(
                sql.indexOf("INSERT INTO `wf_process_definition_version`"),
                sql.indexOf("INSERT INTO `wf_ru_process_instance`"));
        assertThat(definitionSeed)
                .doesNotContain("202604280101000030")
                .doesNotContain("??");
    }

    @Test
    void h2SeedWorkflowInstancesShouldContainBusinessVariables() throws Exception {
        String sql = Files.readString(Path.of("src/test/resources/db/migration-h2/V1__init_h2.sql"));

        assertThat(sql)
                .contains("INSERT INTO wf_ru_process_instance")
                .contains("variables_json")
                .contains("\"leaveType\":\"ANNUAL\"")
                .contains("\"reason\":\"测试请假申请。\"");
    }
}
