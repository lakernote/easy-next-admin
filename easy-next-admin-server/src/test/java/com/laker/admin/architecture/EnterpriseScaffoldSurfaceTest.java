package com.laker.admin.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseScaffoldSurfaceTest {
    private static final Pattern MUTATION_MAPPING_PATTERN = Pattern.compile("@(Post|Put|Patch|Delete)Mapping");

    @Test
    void enterpriseBaselineDoesNotExposeDemoBusinessModule() throws IOException {
        assertThat(Path.of("src/main/java/com/laker/admin/module/demo")).doesNotExist();
        assertThat(Path.of("src/test/java/com/laker/admin/module/demo")).doesNotExist();
        assertThat(Path.of("src/test/java/com/laker/admin/example")).doesNotExist();

        String mysqlV1 = Files.readString(Path.of("src/main/resources/db/migration/V1__init.sql"));
        String h2V1 = Files.readString(Path.of("src/test/resources/db/migration-h2/V1__init_h2.sql"));
        String authService = Files.readString(Path.of("src/main/java/com/laker/admin/infrastructure/security/service/EasyAuthService.java"));
        String passwordHasher = Files.readString(Path.of("src/main/java/com/laker/admin/infrastructure/security/support/EasyPasswordHasher.java"));

        assertThat(mysqlV1).doesNotContain("demo_leave", "demo_product");
        assertThat(h2V1).doesNotContain("demo_leave", "demo_product");
        assertThat(authService).doesNotContain("needsRehash");
        assertThat(passwordHasher).doesNotContain("needsRehash", "历史哈希");
    }

    @Test
    void enterpriseBaselineKeepsImportExportInsideBusinessModules() throws IOException {
        assertThat(Path.of("src/main/java/com/laker/admin/module/transfer")).doesNotExist();
        assertThat(Path.of("src/test/java/com/laker/admin/module/transfer")).doesNotExist();

        String mysqlV1 = Files.readString(Path.of("src/main/resources/db/migration/V1__init.sql"));
        String h2V1 = Files.readString(Path.of("src/test/resources/db/migration-h2/V1__init_h2.sql"));
        String permissions = Files.readString(Path.of("src/main/java/com/laker/admin/infrastructure/security/permission/EasyPermissions.java"));
        String agentGuide = Files.readString(Path.of("../AGENTS.md"));
        String readme = Files.readString(Path.of("../README.md"));
        String featureDocs = Files.readString(Path.of("../docs/features-and-components.md"));
        String architectureDocs = Files.readString(Path.of("../docs/architecture.md"));

        assertThat(mysqlV1).doesNotContain("import_export_task", "/tool/import-export", "import-export:");
        assertThat(h2V1).doesNotContain("import_export_task", "/tool/import-export", "import-export:");
        assertThat(permissions).doesNotContain("ImportExport");
        assertThat(agentGuide + readme + featureDocs + architectureDocs).doesNotContain("导入导出中心");
        assertThat(permissions).contains("USER_IMPORT", "USER_EXPORT");
    }

    @Test
    void enterpriseBaselineDoesNotUseStaticSpringContextFacade() throws IOException {
        assertThat(Path.of("src/main/java/com/laker/admin/common/util/SpringUtils.java")).doesNotExist();

        String scheduleJobManager = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/schedule/core/ScheduleJobManager.java"));
        assertThat(scheduleJobManager).doesNotContain("SpringUtils");
    }

    @Test
    void productionBaselineDoesNotEnableVerboseFeignLogging() throws IOException {
        String baseConfig = Files.readString(Path.of("src/main/resources/application.yaml"));
        String localConfig = Files.readString(Path.of("src/main/resources/application-local.yaml"));

        assertThat(baseConfig).doesNotContain("logger-level: full");
        assertThat(localConfig).contains("logger-level: full");
    }

    @Test
    void ciAndDeploymentBaselineShouldKeepLocalProdProfilesAndNginxHardening() throws IOException {
        Path workflow = Path.of("../.github/workflows/ci.yml");
        Path dockerfile = Path.of("Dockerfile");
        Path localProfile = Path.of("src/main/resources/application-local.yaml");
        Path prodProfile = Path.of("src/main/resources/application-prod.yaml");
        Path nginxConfig = Path.of("../easy-next-admin-web/nginx.conf");

        assertThat(workflow).exists();
        assertThat(dockerfile).exists();
        assertThat(Path.of("src/main/resources/application-intranet.yaml")).doesNotExist();
        assertThat(localProfile).exists();
        assertThat(prodProfile).exists();

        String ci = Files.readString(workflow);
        String serverDockerfile = Files.readString(dockerfile);
        String local = Files.readString(localProfile);
        String prod = Files.readString(prodProfile);
        String nginx = Files.readString(nginxConfig);

        assertThat(ci).contains(
                "mvn -pl easy-next-admin-server -am verify",
                "npm run test:unit",
                "npm run build");
        assertThat(serverDockerfile).contains(
                "SPRING_PROFILES_ACTIVE=prod",
                "HEALTHCHECK",
                "exec java");
        assertThat(local).contains(
                "logger-level: full",
                "show-details: always",
                "maximum-pool-size: 10");
        assertThat(prod).contains(
                "allowed-origins: []",
                "hsts-enabled: true",
                "include: health,info",
                "MYSQL_POOL_MAX_SIZE",
                "EASY_BUSINESS_POOL_MAX_SIZE");
        assertThat(nginx).contains(
                "X-Content-Type-Options",
                "Referrer-Policy",
                "Permissions-Policy",
                "Cache-Control \"public, max-age=31536000, immutable\"");
    }

    @Test
    void webSecurityBaselineUsesAllowlistedCorsAndConfigurableSecurityHeaders() throws IOException {
        String baseConfig = Files.readString(Path.of("src/main/resources/application.yaml"));
        String corsFilter = Files.readString(
                Path.of("src/main/java/com/laker/admin/infrastructure/web/filter/EasyCorsFilter.java"));
        String wafFilter = Files.readString(
                Path.of("src/main/java/com/laker/admin/infrastructure/web/waf/WafFilter.java"));

        assertThat(baseConfig).contains(
                "allowed-origins:",
                "allowed-origin-patterns:",
                "security-headers:",
                "content-security-policy:",
                "hsts-enabled: false");
        assertThat(corsFilter).contains("checkOrigin(requestOrigin)");
        assertThat(corsFilter).doesNotContain("setAccessControlAllowOrigin(requestOrigin)");
        assertThat(wafFilter).contains(
                "Content-Security-Policy",
                "Strict-Transport-Security",
                "Referrer-Policy",
                "Permissions-Policy");
    }

    @Test
    void enterpriseBaselineKeepsModuleOwnershipAndPackageNamesClean() throws IOException {
        assertThat(Path.of("src/main/java/com/laker/admin/module/report/controller/EnterpriseReportController.java")).exists();
        assertThat(Path.of("../easy-next-admin-web/src/features/report/api.ts")).exists();

        assertThat(Path.of("src/main/java/com/laker/admin/module/monitor/controller/WebLogController.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/controller/WebLogController.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/web/error/EasyDefaultUncaughtErrorController.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/web/error/EasyDefaultUncaughtErrorControllor.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/web/handler/ExternalIpHandler.java")).doesNotExist();
        assertThat(Path.of("src/test/java/com/laker/admin/infrastructure/web/handler/ExternalIpHandlerTest.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/NginxQo.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/remote")).doesNotExist();
        assertThat(Path.of("src/test/java/com/laker/admin/integration/RemoteAPIIT.java")).doesNotExist();

        String treeUtil = Files.readString(Path.of("src/main/java/com/laker/admin/common/util/EasyTreeUtil.java"));
        String feignConfig = Files.readString(Path.of("src/main/java/com/laker/admin/config/remote/EasyFeignConfig.java"));
        String circuitBreakerConfig = Files.readString(
                Path.of("src/main/java/com/laker/admin/config/remote/EasyCircuitBreakerConfig.java"));
        String remoteMetricsAspect = Files.readString(
                Path.of("src/main/java/com/laker/admin/infrastructure/observability/metrics/RemoteCallMetricsAspect.java"));
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(treeUtil).doesNotContain("module.system.dto.MenuVo");
        assertThat(feignConfig).contains("@EnableFeignClients(basePackages = \"com.laker.admin.module\")");
        assertThat(feignConfig).contains("Logger.Level.BASIC");
        assertThat(remoteMetricsAspect).contains("org.springframework.cloud.openfeign.FeignClient");
        assertThat(remoteMetricsAspect).doesNotContain("com.laker.admin.module.remote.feign", "com.laker.admin.module.remote.rest");
        assertThat(circuitBreakerConfig + applicationYaml).doesNotContain(
                "ipifyClient",
                "backendA",
                "someShared",
                "normalflux",
                "circuit error");

        List<String> systemEntityNames = fileNames(Path.of("src/main/java/com/laker/admin/module/system/entity"));
        assertThat(systemEntityNames)
                .contains("SysMenuResource.java", "SysRolePermission.java")
                .doesNotContain("SysPower.java", "SysRolePower.java");
    }

    @Test
    void serverCodeShouldAvoidFieldInjectionAndLegacyAcronymNames() throws IOException {
        Path javaRoot = Path.of("src/main/java/com/laker/admin");
        List<String> fieldInjections = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> collectAutowiredFields(path, fieldInjections));
        }
        assertThat(fieldInjections).isEmpty();

        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/lock/base/AbstractEasyLocker.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/lock/base/AbstractSimpleIEasyLocker.java")).doesNotExist();
        assertThat(fileNames(Path.of("src/main/java/com/laker/admin/infrastructure/thread")))
                .contains("EasyNextAdminMdcThreadPoolExecutor.java")
                .doesNotContain("EasyNextAdminMDCThreadPoolExecutor.java");

        String auditApiLogController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/audit/controller/AuditApiLogController.java"));
        String threadPoolConfig = Files.readString(
                Path.of("src/main/java/com/laker/admin/config/thread/EasyThreadPoolConfig.java"));
        assertThat(auditApiLogController)
                .contains("visits7Day()", "visitsTop10Ip()")
                .doesNotContain("visits7day()", "visitsTop10IP()");
        assertThat(threadPoolConfig)
                .contains("businessMdcThreadPool()")
                .doesNotContain("businessMDCThreadPool()");
    }

    @Test
    void productionBaselineDoesNotAutoExposeExampleOrExplosiveRuntimeBeans() throws IOException {
        String localMessageTemplate = Files.readString(
                Path.of("src/main/java/com/laker/admin/infrastructure/message/local/EasyLocalMessageTemplate.java"));
        String kafkaConsumer = Files.readString(
                Path.of("src/main/java/com/laker/admin/infrastructure/mq/kafka/consumer/EasyKafkaConsumer.java"));

        assertThat(Path.of("src/main/java/com/laker/admin/module/remote")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/sample")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/message/local/ExampleILocalMessageOperation.java"))
                .doesNotExist();
        assertThat(localMessageTemplate).doesNotContain("System.out.println", "exampleMethod", "local-message-example");
        assertThat(kafkaConsumer).doesNotContain("test exception", "RuntimeException(\"test exception\")");
    }

    @Test
    void userFacingApisDoNotExposePersistenceEntitiesForMessageAndFileCenter() throws IOException {
        String userMessageController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/message/controller/UserMessageController.java"));
        String userMessageService = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/message/service/UserMessageService.java"));
        String sysFileController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/system/controller/SysFileController.java"));
        String scheduleJobController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/schedule/controller/ScheduleJobController.java"));
        String scheduleJobLogController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/schedule/controller/ScheduleJobLogController.java"));
        String profileSecurityController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/system/controller/ProfileSecurityController.java"));
        String profileSecurityService = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/system/service/profile/ProfileSecurityService.java"));
        String profileSecurityOverview = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/system/dto/profile/ProfileSecurityOverview.java"));
        String authController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/system/controller/AuthController.java"));
        String monitorStatisticsController = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/monitor/controller/MonitorStatisticsController.java"));
        String monitorStatisticsOverview = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/monitor/dto/MonitorStatisticsOverview.java"));
        String auditControllers = Files.readString(Path.of("src/main/java/com/laker/admin/module/audit/controller/AuditApiLogController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/audit/controller/AuditDataChangeLogController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/audit/controller/AuditErrorLogController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/audit/controller/AuditLoginLogController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/audit/controller/AuditOperationLogController.java"));
        String workflowControllers = Files.readString(Path.of("src/main/java/com/laker/admin/module/workflow/controller/WfProcessDefinitionController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/workflow/controller/WfProcessInstanceController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/workflow/controller/WfTaskController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/workflow/leave/controller/LeaveRequestController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/workflow/purchase/controller/PurchaseRequestController.java"))
                + Files.readString(Path.of("src/main/java/com/laker/admin/module/workflow/repair/controller/RepairRequestController.java"));
        String workflowTaskListItem = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfTaskListItem.java"));
        String workflowCcListItem = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfCcListItem.java"));
        String workflowInstanceDetail = Files.readString(
                Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfProcessInstanceDetail.java"));

        assertThat(Path.of("src/main/java/com/laker/admin/module/message/dto/UserMessageView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/SysFileView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/schedule/dto/ScheduleJobRequest.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/schedule/dto/ScheduleJobView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/schedule/dto/ScheduleJobLogView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/profile/ProfileLoginHistoryView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/OnlineSessionView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/auth/AuthLoginRequest.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/auth/AuthTokenResponse.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/auth/AuthUserProfile.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/auth/CaptchaResponse.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/system/dto/auth/DemoAccountResponse.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/security/model/AuthLoginRequest.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/security/model/AuthTokenResponse.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/security/model/AuthUserProfile.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/security/model/CaptchaResponse.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/infrastructure/security/model/DemoAccountResponse.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditApiLogView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditApiDailyVisitView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditApiTopIpView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditDataChangeLogView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditErrorLogView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditLoginLogView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditOperationLogView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/dto/AuditUserView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/audit/vo")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfProcessDefinitionView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfProcessInstanceView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfProcessDefinitionVersionView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfTaskView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfEventView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/dto/WfCcView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/leave/dto/LeaveRequestView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/purchase/dto/PurchaseRequestView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/repair/dto/RepairRequestView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/repair/dto/RepairAttachmentView.java")).exists();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/leave/dto/LeaveRequestVO.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/purchase/dto/PurchaseRequestVO.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/repair/dto/RepairRequestVO.java")).doesNotExist();
        assertThat(Path.of("src/main/java/com/laker/admin/module/workflow/repair/dto/RepairAttachmentVO.java")).doesNotExist();
        assertThat(userMessageController + userMessageService)
                .doesNotContain("PageResponse<UserMessage>");
        assertThat(sysFileController)
                .doesNotContain("PageResponse<SysFile>", "Response<SysFile>");
        assertThat(scheduleJobController)
                .doesNotContain("PageResponse<ScheduleJob>", "Response<ScheduleJob>", "@RequestBody ScheduleJob ");
        assertThat(scheduleJobLogController)
                .doesNotContain("PageResponse<ScheduleJobLog>");
        assertThat(profileSecurityController + profileSecurityOverview)
                .doesNotContain(
                        "AuditLoginLog",
                        "import com.laker.admin.infrastructure.security.model.OnlineSession",
                        "List<OnlineSession>",
                        "Response<List<OnlineSession>");
        assertThat(profileSecurityService)
                .doesNotContain("public PageResponse<AuditLoginLog>", "public List<AuditLoginLog>");
        assertThat(authController + monitorStatisticsController + monitorStatisticsOverview)
                .doesNotContain(
                        "import com.laker.admin.infrastructure.security.model.OnlineSession",
                        "import com.laker.admin.infrastructure.security.model.AuthLoginRequest",
                        "import com.laker.admin.infrastructure.security.model.AuthTokenResponse",
                        "import com.laker.admin.infrastructure.security.model.CaptchaResponse",
                        "import com.laker.admin.infrastructure.security.model.DemoAccountResponse",
                        "PageResponse<OnlineSession>",
                        "Response<List<OnlineSession>",
                        "List<OnlineSession>");
        assertThat(authController + monitorStatisticsController + monitorStatisticsOverview + profileSecurityController + profileSecurityOverview)
                .contains("OnlineSessionView")
                .doesNotContain("import com.laker.admin.infrastructure.security.model.AuthUserProfile");
        assertThat(auditControllers).doesNotContain(
                "PageResponse<AuditApiLog>",
                "PageResponse<AuditDataChangeLog>",
                "PageResponse<AuditErrorLog>",
                "PageResponse<AuditLoginLog>",
                "PageResponse<AuditOperationLog>",
                "AuditLogStatisticsVo",
                "AuditLogStatisticsTop10Vo",
                "module.audit.vo");
        assertThat(workflowControllers).doesNotContain(
                "PageResponse<WfProcessDefinition>",
                "PageResponse<WfProcessInstance>",
                "Response<WfTask>",
                "LeaveRequestVO",
                "PurchaseRequestVO",
                "RepairRequestVO",
                "RepairAttachmentVO");
        assertThat(workflowTaskListItem).doesNotContain("extends WfTask");
        assertThat(workflowCcListItem).doesNotContain("extends WfCc");
        assertThat(workflowInstanceDetail).doesNotContain(".entity.");
        assertThat(workflowInstanceDetail).contains(
                "WfProcessInstanceView",
                "WfProcessDefinitionView",
                "WfProcessDefinitionVersionView",
                "List<WfTaskView>",
                "List<WfEventView>",
                "List<WfCcView>");
    }

    @Test
    void mutationControllersDeclareSemanticAuditAnnotations() throws IOException {
        Path controllerRoot = Path.of("src/main/java/com/laker/admin/module");
        List<String> missingAudits = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(controllerRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .forEach(path -> collectMutationMethodsWithoutAudit(path, missingAudits));
        }
        assertThat(missingAudits).isEmpty();
    }

    private void collectMutationMethodsWithoutAudit(Path path, List<String> missingAudits) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                if (!MUTATION_MAPPING_PATTERN.matcher(lines.get(i)).find()) {
                    continue;
                }
                int signatureLine = findNextMethodSignature(lines, i);
                if (signatureLine < 0) {
                    continue;
                }
                String annotationBlock = String.join("\n", lines.subList(i, signatureLine + 1));
                String signature = lines.get(signatureLine).trim();
                if (isAllowedMutationWithoutExplicitAudit(path, signature)) {
                    continue;
                }
                if (!annotationBlock.contains("@EasyAudit")) {
                    missingAudits.add(path + " -> " + signature);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取控制器失败：" + path, ex);
        }
    }

    private void collectAutowiredFields(Path path, List<String> fieldInjections) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size() - 1; i++) {
                String annotationLine = lines.get(i).trim();
                if (!annotationLine.matches("@Autowired(\\(.*\\))?")) {
                    continue;
                }
                String nextLine = lines.get(i + 1).trim();
                if (nextLine.endsWith(";") && !nextLine.contains("(")) {
                    fieldInjections.add(path + ":" + (i + 1) + " -> " + nextLine);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Java 文件失败：" + path, ex);
        }
    }

    private List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).toList();
        }
    }

    private int findNextMethodSignature(List<String> lines, int from) {
        for (int i = from + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("public ") && line.contains("(")) {
                return i;
            }
        }
        return -1;
    }

    private boolean isAllowedMutationWithoutExplicitAudit(Path path, String signature) {
        return path.endsWith("AuthController.java") && signature.contains(" login(");
    }
}
