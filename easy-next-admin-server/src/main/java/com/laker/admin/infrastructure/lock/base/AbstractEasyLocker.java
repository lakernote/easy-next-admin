package com.laker.admin.infrastructure.lock.base;

import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.lock.IEasyLocker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * 分布式锁
 *
 * @author easynext
 */
@Slf4j
public abstract class AbstractEasyLocker implements IEasyLocker {
    private final TaskScheduler taskScheduler;

    protected AbstractEasyLocker(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public EasyLocker tryAcquire(final String key, final Duration expiration) {
        final String token = EasyIdGenerator.uuid32();
        boolean acquire = acquire(key, token, expiration);
        if (!acquire) {
            // 获取锁失败
            log.debug("Failed to acquire lock for key {} with token {}", key, token);
            return null;
        }
        log.debug("Acquired lock for key {} with token {}", key, token);
        // 后台线程定时续租 一个锁一个后台线程续约
        ScheduledFuture<?> scheduledFuture = scheduleLockRefresh(key, token, expiration);
        return EasyLocker.builder()
                .key(key)
                .token(token)
                .scheduledFuture(scheduledFuture).build();
    }

    @Override
    public boolean release(EasyLocker lock) {
        if (lock == null) {
            log.warn("Lock is null");
            return false;
        }
        cancelSchedule(lock);
        // 释放锁
        log.debug("Releasing lock for key {} with token {}", lock.getKey(), lock.getToken());
        return release0(lock);
    }

    /**
     * 续租锁
     *
     * @param key        锁定的key
     * @param token      锁的token
     * @param expiration 锁过期时间
     */
    private ScheduledFuture<?> scheduleLockRefresh(final String key, final String token, final Duration expiration) {
        final Duration duration = Duration.ofMillis(expiration.toMillis() / 3);
        final PeriodicTrigger periodicTrigger = new PeriodicTrigger(duration);
        periodicTrigger.setInitialDelay(duration);
        return taskScheduler.schedule(() ->
        {
            try {
                refresh(key, token, expiration);
            } catch (Exception e) {
                // 续租失败，可能是锁已经被释放了
                log.error("Failed to refresh lock for key {} with token {}", key, token, e);
            }
        }, periodicTrigger);     // 续租时间为锁的三分之一

    }

    /**
     * 取消续租
     *
     * @param lock 锁
     */
    private void cancelSchedule(EasyLocker lock) {
        final ScheduledFuture<?> scheduledFuture = lock.getScheduledFuture();
        if (scheduledFuture != null && !scheduledFuture.isCancelled() && !scheduledFuture.isDone()) {
            // 取消续租
            // 这里需要注意，cancel(true)会中断正在执行的任务
            log.debug("Cancel schedule for lock {} with token {}", lock.getKey(), lock.getToken());
            scheduledFuture.cancel(true);
        }
    }

    /**
     * 获取锁
     *
     * @param key        锁定的key
     * @param token      锁的token
     * @param expiration 锁过期时间
     * @return 锁定失败返回null
     */
    protected abstract boolean acquire(String key, String token, Duration expiration);

    /**
     * 释放锁
     *
     * @param lock 锁
     * @return 是否成功
     */
    protected abstract boolean release0(EasyLocker lock);

    /**
     * 刷新锁过期时间
     *
     * @param key        锁定的key
     * @param token      锁的token
     * @param expiration 锁过期时间
     * @return 是否成功
     */
    protected abstract boolean refresh(String key, String token, Duration expiration);
}
