package com.laker.admin.module.assistant.job;

import com.laker.admin.module.schedule.core.EasyJob;
import com.laker.admin.module.schedule.core.EasyJobHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@EasyJob(
        jobCode = AssistantRetentionCleanupJob.JOB_CODE,
        jobName = "助手运行记录保留清理",
        cron = "0 0 3 * * ?"
)
@Slf4j
public class AssistantRetentionCleanupJob implements EasyJobHandler {
    public static final String JOB_CODE = "ai_assistant_retention_cleanup";

    @Override
    public void execute(Map<String, Object> params) {
        // 当前阶段 Run Record 还只随 HTTP 响应返回，尚未落库；保留这个 no-op 执行器，
        // 是为了让已登记的动态任务能正常装载，后续接入 AssistantRunStore 后在这里做过期清理。
        log.debug("Assistant retention cleanup skipped because persistent run store is not enabled yet.");
    }
}
