package com.laker.admin.infrastructure.thread;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 自定义扩展线程池用于捕获执行中异常，防止异常被吞。
 * Trace 与 MDC 传播只使用 Meerkat 提供的可选 {@link TaskDecorator}。
 *
 * @author laker
 */
@Slf4j
public class EasyNextAdminMdcThreadPoolExecutor extends EasyNextAdminThreadPoolExecutor {
    private final TaskDecorator taskDecorator;
    /**
     * 简易线程池构造器
     *
     * @param poolSize  线程池大小,e.g:60
     * @param queueSize 等待队列,e.g:1000
     * @param prefix    线程名前缀,e.g:MCP-POOL
     */
    public EasyNextAdminMdcThreadPoolExecutor(int poolSize, int queueSize, String prefix) {
        this(poolSize, queueSize, prefix, null);
    }

    /**
     * Creates an executor that optionally propagates Meerkat Trace/MDC context.
     *
     * @param traceTaskDecorator optional decorator supplied by the enabled observability Starter
     */
    public EasyNextAdminMdcThreadPoolExecutor(int poolSize, int queueSize, String prefix,
                                              TaskDecorator traceTaskDecorator) {
        super(poolSize, queueSize, prefix);
        this.taskDecorator = traceTaskDecorator;
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
        Runnable decorated = taskDecorator == null ? command : taskDecorator.decorate(command);
        if (command instanceof RunnableFuture<?> future) {
            // submit future
            super.execute(new EasyNextAdminFuture<>(future, decorated));
        } else {
            super.execute(decorated);
        }
    }

    private static class EasyNextAdminFuture<T> implements RunnableFuture<T> {
        private final RunnableFuture<T> future;
        private final Runnable decorated;

        @SuppressWarnings("unchecked")
        public EasyNextAdminFuture(RunnableFuture<?> future, Runnable decorated) {
            this.future = (RunnableFuture<T>) future;
            this.decorated = decorated;
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
            decorated.run();
        }

    }
}
