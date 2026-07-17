package com.laker.admin.infrastructure.thread;

import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 自定义扩展线程池用于捕获执行中异常，防止异常被吞 + 解决MDC参数问题。
 *
 * @author laker
 */
@Slf4j
public class EasyNextAdminMdcThreadPoolExecutor extends EasyNextAdminThreadPoolExecutor {
    /**
     * 简易线程池构造器
     *
     * @param poolSize  线程池大小,e.g:60
     * @param queueSize 等待队列,e.g:1000
     * @param prefix    线程名前缀,e.g:MCP-POOL
     */
    public EasyNextAdminMdcThreadPoolExecutor(int poolSize, int queueSize, String prefix) {
        super(poolSize, queueSize, prefix);
        // 非核心线程如果处于闲置状态超过该值，就会被销毁。如果设置allowCoreThreadTimeOut(true)，则会也作用于核心线程。
        this.allowCoreThreadTimeOut(true);
    }

    /**
     * submit Runnable callable 都会走这里，所以我们只需要改写这里即可。
     *
     * @param command
     */
    @Override
    public void execute(Runnable command) {
        if (command instanceof RunnableFuture) {
            // submit future
            super.execute(new EasyNextAdminFuture((RunnableFuture) command, EasyMdcContext.copy()));
        } else {
            super.execute(wrapExecuteRunnable(command, EasyMdcContext.copy()));
        }

    }


    /**
     * 封装任务，加入TraceId，无返回值
     *
     * @param runnable
     * @param threadContext
     * @return
     */
    private static Runnable wrapExecuteRunnable(final Runnable runnable, final Map<String, String> threadContext) {
        return wrapWithTrace(runnable, threadContext);
    }

    private static class EasyNextAdminFuture<T> implements RunnableFuture<T> {
        private final RunnableFuture<T> future;
        private final Map<String, String> threadContext;

        public EasyNextAdminFuture(RunnableFuture<T> future, Map<String, String> threadContext) {
            this.future = future;
            this.threadContext = threadContext;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return this.future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return this.future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return this.future.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return this.future.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return this.future.get(timeout, unit);
        }

        @Override
        public void run() {
            try (EasyMdcContext.Scope ignored = EasyMdcContext.withContext(threadContext)) {
                EasyTraceIdContext.getOrCreateTraceId();
                future.run();
            }

        }

    }

    private static Runnable wrapWithTrace(Runnable runnable, Map<String, String> contextMap) {
        return EasyMdcContext.wrap(() -> {
            EasyTraceIdContext.getOrCreateTraceId();
            runnable.run();
        }, contextMap);
    }
}
