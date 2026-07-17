package com.laker.admin.module.workflow.controller;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionRequest;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class WfProcessDefinitionControllerTest {
    private static final long OPERATOR_ID = 202604280101000001L;

    @Autowired
    private WfProcessDefinitionController controller;
    @Autowired
    private IWfProcessDefinitionService definitionService;
    @Autowired
    private IWfProcessDefinitionVersionService versionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void saveShouldUpdateCurrentVersionWithoutIncreasingVersionNumber() {
        WfProcessDefinition definition = createDefinition("save_current_version");

        WfProcessDefinitionRequest request = new WfProcessDefinitionRequest();
        request.setId(definition.getId());
        request.setProcessKey(definition.getProcessKey());
        request.setProcessName("保存覆盖当前版本");
        request.setStatus("DRAFT");
        request.setGraphJson(graph("edited"));

        controller.save(request);

        WfProcessDefinition savedDefinition = definitionService.getById(definition.getId());
        assertThat(savedDefinition.getCurrentVersion()).isEqualTo(1);
        assertThat(versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definition.getId())
                .count()).isEqualTo(1);
        assertThat(currentVersion(definition.getId()).getGraphJson()).contains("edited");
    }

    @Test
    void saveShouldSyncCurrentVersionNodeAndTransitionProjection() {
        WfProcessDefinition definition = createDefinition("save_projection");

        WfProcessDefinitionRequest request = new WfProcessDefinitionRequest();
        request.setId(definition.getId());
        request.setProcessKey(definition.getProcessKey());
        request.setProcessName("保存结构化投影");
        request.setStatus("DRAFT");
        request.setGraphJson(graph("投影审批"));

        controller.save(request);

        WfProcessDefinitionVersion version = currentVersion(definition.getId());
        assertThat(countRows("wf_process_node", version.getId())).isEqualTo(3);
        assertThat(countRows("wf_process_transition", version.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select node_name from wf_process_node
                where version_id = ? and node_key = 'approve'
                """, String.class, version.getId())).isEqualTo("投影审批");
        assertThat(jdbcTemplate.queryForObject("""
                select approver_type from wf_process_node
                where version_id = ? and node_key = 'approve'
                """, String.class, version.getId())).isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject("""
                select condition_type from wf_process_transition
                where version_id = ? and from_node_key = 'start' and to_node_key = 'approve'
                """, String.class, version.getId())).isEqualTo("ALWAYS");
    }

    @Test
    void publishShouldCreateNextVersionFromCurrentGraphAndEnableDefinition() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(OPERATOR_ID)
                .userName("admin")
                .build());
        WfProcessDefinition definition = createDefinition("publish_next_version");

        controller.publish(definition.getId());

        WfProcessDefinition publishedDefinition = definitionService.getById(definition.getId());
        assertThat(publishedDefinition.getCurrentVersion()).isEqualTo(2);
        assertThat(publishedDefinition.getStatus()).isEqualTo("ENABLED");
        assertThat(versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definition.getId())
                .count()).isEqualTo(2);
        WfProcessDefinitionVersion publishedVersion = currentVersion(definition.getId());
        assertThat(publishedVersion.getVersion()).isEqualTo(2);
        assertThat(publishedVersion.getGraphJson()).contains("initial");
        assertThat(publishedVersion.getStatus()).isEqualTo("PUBLISHED");
        assertThat(publishedVersion.getPublishedBy()).isEqualTo(OPERATOR_ID);
    }

    @Test
    void publishShouldSyncPublishedVersionNodeAndTransitionProjection() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(OPERATOR_ID)
                .userName("admin")
                .build());
        WfProcessDefinition definition = createDefinition("publish_projection");

        controller.publish(definition.getId());

        WfProcessDefinitionVersion publishedVersion = currentVersion(definition.getId());
        assertThat(countRows("wf_process_node", publishedVersion.getId())).isEqualTo(3);
        assertThat(countRows("wf_process_transition", publishedVersion.getId())).isEqualTo(2);
    }

    @Test
    void publishShouldRejectWhenNextVersionAlreadyExists() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(OPERATOR_ID)
                .userName("admin")
                .build());
        WfProcessDefinition definition = createDefinition("publish_existing_next_version");
        WfProcessDefinitionVersion nextVersion = new WfProcessDefinitionVersion();
        nextVersion.setDefinitionId(definition.getId());
        nextVersion.setVersion(2);
        nextVersion.setGraphJson(graph("already-published"));
        nextVersion.setStatus("PUBLISHED");
        nextVersion.setPublishedBy(OPERATOR_ID);
        nextVersion.setPublishedAt(LocalDateTime.now());
        nextVersion.setCreatedBy(OPERATOR_ID);
        nextVersion.setCreatedAt(LocalDateTime.now());
        nextVersion.setUpdatedBy(OPERATOR_ID);
        nextVersion.setUpdatedAt(LocalDateTime.now());
        versionService.save(nextVersion);

        assertThatThrownBy(() -> controller.publish(definition.getId()))
                .hasMessageContaining("流程定义版本已变化");

        WfProcessDefinition unchangedDefinition = definitionService.getById(definition.getId());
        assertThat(unchangedDefinition.getCurrentVersion()).isEqualTo(1);
        assertThat(versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definition.getId())
                .eq(WfProcessDefinitionVersion::getVersion, 2)
                .count()).isEqualTo(1);
    }

    @Test
    void publishShouldRejectDefinitionWithMissingFixedAssignee() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(OPERATOR_ID)
                .userName("admin")
                .build());
        WfProcessDefinition definition = createDefinition("publish_missing_assignee", graph("无效审批人", 999_999L));

        assertThatThrownBy(() -> controller.publish(definition.getId()))
                .hasMessageContaining("不存在或已停用");

        WfProcessDefinition unchangedDefinition = definitionService.getById(definition.getId());
        assertThat(unchangedDefinition.getCurrentVersion()).isEqualTo(1);
        assertThat(versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definition.getId())
                .eq(WfProcessDefinitionVersion::getVersion, 2)
                .count()).isZero();
    }

    private WfProcessDefinition createDefinition(String processKey) {
        return createDefinition(processKey, graph("initial"));
    }

    private WfProcessDefinition createDefinition(String processKey, String graphJson) {
        LocalDateTime now = LocalDateTime.now();
        WfProcessDefinition definition = new WfProcessDefinition();
        definition.setProcessKey(processKey);
        definition.setProcessName(processKey);
        definition.setCurrentVersion(1);
        definition.setStatus("DRAFT");
        definition.setCreatedBy(OPERATOR_ID);
        definition.setCreatedAt(now);
        definition.setUpdatedBy(OPERATOR_ID);
        definition.setUpdatedAt(now);
        definitionService.save(definition);

        WfProcessDefinitionVersion version = new WfProcessDefinitionVersion();
        version.setDefinitionId(definition.getId());
        version.setVersion(1);
        version.setGraphJson(graphJson);
        version.setStatus("PUBLISHED");
        version.setPublishedBy(OPERATOR_ID);
        version.setPublishedAt(now);
        version.setCreatedBy(OPERATOR_ID);
        version.setCreatedAt(now);
        version.setUpdatedBy(OPERATOR_ID);
        version.setUpdatedAt(now);
        versionService.save(version);
        return definition;
    }

    private WfProcessDefinitionVersion currentVersion(Long definitionId) {
        WfProcessDefinition definition = definitionService.getById(definitionId);
        return versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definitionId)
                .eq(WfProcessDefinitionVersion::getVersion, definition.getCurrentVersion())
                .one();
    }

    private Integer countRows(String tableName, Long versionId) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName + " where version_id = ?", Integer.class, versionId);
    }

    private String graph(String label) {
        return graph(label, OPERATOR_ID);
    }

    private String graph(String label, long assigneeId) {
        return """
                {"nodes":[
                  {"id":"start","type":"circle","text":"开始","properties":{"nodeType":"START"}},
                  {"id":"approve","type":"rect","text":"%s","properties":{"nodeType":"APPROVAL","approveType":"ANY_ONE","approverType":"USER","assigneeIds":[%d]}},
                  {"id":"end","type":"circle","text":"结束","properties":{"nodeType":"END"}}
                ],"edges":[
                  {"sourceNodeId":"start","targetNodeId":"approve"},
                  {"sourceNodeId":"approve","targetNodeId":"end"}
                ]}
                """.formatted(label, assigneeId);
    }
}
