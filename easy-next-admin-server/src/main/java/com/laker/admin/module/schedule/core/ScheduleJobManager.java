package com.laker.admin.module.schedule.core;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.module.schedule.enums.JobStateEnum;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobManager implements CommandLineRunner {
    private static final String LOCK_KEY_PREFIX = "schedule:job:";
    private static final Duration LOCK_EXPIRATION = Duration.ofMinutes(30);

    private final ThreadPoolTaskScheduler easyTaskThreadPool;
    private final ScheduleJobStore scheduleJobStore;
    private final List<ScheduleJobCallback> jobCallbacks;
    private final IScheduleJobService scheduleJobService;
    private final Map<String, EasyJobHandler> jobHandlers;
    private final IEasyLocker easyLocker;

    /** 当前 JVM 内已经注册的动态任务句柄，用于启动、暂停和查看运行态。 */
    private final Map<String, ScheduledFuture<?>> jvmRunningJobs = new ConcurrentHashMap<>();

    public ScheduleJobManager(ThreadPoolTaskScheduler easyTaskThreadPool,
                              ScheduleJobStore scheduleJobStore,
                              List<ScheduleJobCallback> jobCallbacks,
                              IScheduleJobService scheduleJobService,
                              Map<String, EasyJobHandler> jobHandlers,
                              IEasyLocker easyLocker) {
        this.easyTaskThreadPool = easyTaskThreadPool;
        this.scheduleJobStore = scheduleJobStore;
        this.jobCallbacks = jobCallbacks;
        this.scheduleJobService = scheduleJobService;
        this.jobHandlers = jobHandlers;
        this.easyLocker = easyLocker;
    }

    @Override
    public void run(String... args) throws Exception {
        // 启动时只扫描 @EasyJob 声明，最终是否调度以数据库 schedule_job 的启停状态为准。
        if (CollectionUtils.isEmpty(jobHandlers)) {
            log.warn("未查询到 EasyJobHandler 实现类");
            return;
        }
        jobHandlers.forEach((s, job) -> {
            EasyJob annotation = AnnotationUtils.findAnnotation(job.getClass(), EasyJob.class);
            if (annotation != null) {
                String cron = annotation.cron();
                String jobName = annotation.jobName();
                String jobCode = annotation.jobCode();
                ScheduleJobDefinition jobDefinition = ScheduleJobDefinition.builder()
                        .jobCode(jobCode)
                        .jobName(jobName)
                        .cronExpression(cron)
                        .jobClassName(job.getClass().getName()).build();
                scheduleJobStore.saveDefinition(jobDefinition);
                ScheduleJobDefinition storedJobDefinition = scheduleJobStore.findByJobCode(jobCode);
                if (Boolean.TRUE.equals(storedJobDefinition.getEnable()) && JobStateEnum.START.equals(storedJobDefinition.getJobState())) {
                    this.startJvmJob(storedJobDefinition, null);
                }
            }
        });
    }

    /** 将数据库中启用的任务注册到当前 JVM 调度器。 */
    private void startJvmJob(ScheduleJobDefinition jobDefinition, Map param) {
        String cronExpression = jobDefinition.getCronExpression();
        if (StringUtils.hasText(cronExpression) && !Objects.equals(cronExpression, "-")) {
            ScheduledFuture<?> future = easyTaskThreadPool
                    .schedule(() -> {
                                EasyLocker lock = easyLocker.tryAcquire(LOCK_KEY_PREFIX + jobDefinition.getJobCode(), LOCK_EXPIRATION);
                                if (lock == null) {
                                    log.debug("Skip schedule job because another instance owns the lock, code={}, name={}",
                                            jobDefinition.getJobCode(), jobDefinition.getJobName());
                                    return;
                                }
                                try {
                                    jobCallbacks.forEach(jobCallback -> jobCallback.start(jobDefinition));
                                    String jobClassName = jobDefinition.getJobClassName();
                                    EasyJobHandler jobHandler = findJobHandler(jobClassName);
                                    jobHandler.execute(param);

                                } catch (Exception e) {
                                    jobCallbacks.forEach(jobCallback -> jobCallback.exception(jobDefinition, e));
                                } finally {
                                    jobCallbacks.forEach(jobCallback -> jobCallback.end(jobDefinition));
                                    easyLocker.release(lock);
                                }
                            },
                            new CronTrigger(cronExpression));
            jvmRunningJobs.put(jobDefinition.getJobCode(), future);
        } else {
            log.warn("cron表达式为：{}，Job信息：{}，不予启动任务。", cronExpression, jobDefinition);
        }
    }

    private void removeJvmJob(String jobCode) {
        ScheduledFuture<?> scheduledFuture = jvmRunningJobs.get(jobCode);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        jvmRunningJobs.remove(jobCode);
    }

    public List<String> jvmJobList() {
        List<String> res = new ArrayList<>();
        res.addAll(jvmRunningJobs.keySet());
        log.info(easyTaskThreadPool.getScheduledThreadPoolExecutor().toString());
        return res;
    }


    public synchronized void startJob(String jobCode) {
        ScheduleJobDefinition jobDefinition = scheduleJobStore.findByJobCode(jobCode);
        if (Boolean.TRUE.equals(jobDefinition.getEnable())) {
            removeJvmJob(jobCode);
            startJvmJob(jobDefinition, null);
            scheduleJobService.update(Wrappers.<ScheduleJob>lambdaUpdate().set(ScheduleJob::getJobState, JobStateEnum.START).eq(ScheduleJob::getJobCode, jobCode));
        }

    }


    public synchronized void stopJob(String jobCode) {
        removeJvmJob(jobCode);
        scheduleJobService.update(Wrappers.<ScheduleJob>lambdaUpdate().set(ScheduleJob::getJobState, JobStateEnum.STOP).eq(ScheduleJob::getJobCode, jobCode));
    }

    private EasyJobHandler findJobHandler(String jobClassName) {
        for (EasyJobHandler jobHandler : jobHandlers.values()) {
            if (Objects.equals(jobClassName, jobHandler.getClass().getName())) {
                return jobHandler;
            }
        }
        throw new IllegalStateException("未找到任务执行器：" + jobClassName);
    }


}
