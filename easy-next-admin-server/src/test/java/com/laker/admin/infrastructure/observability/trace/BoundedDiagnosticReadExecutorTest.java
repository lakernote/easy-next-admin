package com.laker.admin.infrastructure.observability.trace;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class BoundedDiagnosticReadExecutorTest {

    @Test
    void shouldBoundConcurrentScansAndEventuallyRunEveryReader() throws Exception {
        BoundedDiagnosticReadExecutor reads = new BoundedDiagnosticReadExecutor(2, Duration.ofSeconds(5));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger entered = new AtomicInteger();
        CountDownLatch firstTwoEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(pool.submit(() -> reads.execute(() -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    entered.incrementAndGet();
                    firstTwoEntered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        active.decrementAndGet();
                    }
                    return current;
                })));
            }

            assertThat(firstTwoEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(entered.get()).isEqualTo(2);
            release.countDown();
            for (Future<Integer> future : futures) future.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(entered.get()).isEqualTo(8);
        assertThat(maximum.get()).isEqualTo(2);
    }

    @Test
    void shouldAllowNestedReadOnTheSameThread() {
        BoundedDiagnosticReadExecutor reads = new BoundedDiagnosticReadExecutor(1);

        String result = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> reads.execute(() -> reads.execute(() -> "ok")));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRejectQuicklyInsteadOfQueuingServletThreadsBehindAFullReaderBudget() throws Exception {
        BoundedDiagnosticReadExecutor reads = new BoundedDiagnosticReadExecutor(1, Duration.ofMillis(25));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        Future<String> owner = pool.submit(() -> reads.execute(() -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "owner";
        }));
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertThatThrownBy(() -> reads.execute(() -> "queued"))
                            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                                assertThat(exception.getMessage()).contains("诊断历史正在读取");
                            }));
        } finally {
            release.countDown();
            assertThat(owner.get(2, TimeUnit.SECONDS)).isEqualTo("owner");
            pool.shutdownNow();
        }
    }
}
