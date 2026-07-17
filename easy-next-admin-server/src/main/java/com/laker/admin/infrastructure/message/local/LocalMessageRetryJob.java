package com.laker.admin.infrastructure.message.local;

import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.module.schedule.core.EasyJob;
import com.laker.admin.module.schedule.core.EasyJobHandler;
import com.laker.admin.module.schedule.core.ScheduleJobRunContext;
import com.laker.admin.module.schedule.enums.JobExecutionMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@EasyJob(
        jobCode = "infra_local_message_retry",
        jobName = "本地消息失败重试",
        cron = "0 0/1 * * * ?",
        executionMode = JobExecutionMode.BROADCAST
)
@ConditionalOnProperty(prefix = "easy.features", name = "outbox", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LocalMessageRetryJob implements EasyJobHandler {
    private static final int CLAIM_SIZE = 100;
    private static final int MAX_BATCHES_PER_RUN = 5;
    private static final Duration DELIVERY_LEASE = Duration.ofMinutes(5);

    private final LocalMessageDeliveryService deliveryService;

    @Override
    public void execute(Map<String, Object> params) {
        String workerId = currentWorkerId();
        int claimedCount = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<LocalMessage> messages = deliveryService.claimNext(workerId, CLAIM_SIZE, DELIVERY_LEASE);
            if (messages.isEmpty()) {
                break;
            }
            claimedCount += messages.size();
            messages.forEach(deliveryService::deliver);
            if (messages.size() < CLAIM_SIZE) {
                break;
            }
        }
        if (claimedCount > 0) {
            log.info("Local message retry run finished, workerId={}, claimed={}", workerId, claimedCount);
        }
    }

    private String currentWorkerId() {
        ScheduleJobRunContext context = ScheduleJobRunContext.current();
        if (context != null && context.getInstanceId() != null) {
            return context.getInstanceId();
        }
        return "outbox-job:" + EasyIdGenerator.uuid32().substring(0, 12);
    }
}
