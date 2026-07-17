package com.laker.admin.infrastructure.idempotency.duplicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentHashMapDuplicateRequestLimiterTest {
    ConcurrentHashMapDuplicateRequestLimiter concurrentHashMapDuplicateRequestLimiter;

    @BeforeEach
    void setUp() {
        concurrentHashMapDuplicateRequestLimiter = new ConcurrentHashMapDuplicateRequestLimiter();
    }

    @AfterEach
    void tearDown() {
        concurrentHashMapDuplicateRequestLimiter.close();
    }

    @Test
    void tryRequest() {
        assertThat(concurrentHashMapDuplicateRequestLimiter.tryRequest("key", 1)).isTrue();
        assertThat(concurrentHashMapDuplicateRequestLimiter.tryRequest("key", 1)).isFalse();
    }

    @Test
    void cleanUp() {
        concurrentHashMapDuplicateRequestLimiter.cleanUp();
    }

    @Test
        // 并发测试 tryRequest 和 cleanUp
    void concurrentTest() throws InterruptedException {
        final Thread thread1 = new Thread(() -> {
            concurrentHashMapDuplicateRequestLimiter.tryRequest("key", 1);
        });
        final Thread thread2 = new Thread(() -> {
            concurrentHashMapDuplicateRequestLimiter.cleanUp();
        });
        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

    }

    @Test
        // 并发测试 tryRequest 和 tryRequest
    void concurrentTest2() throws InterruptedException {
        final Thread thread1 = new Thread(() -> {
            concurrentHashMapDuplicateRequestLimiter.tryRequest("key", 1);
        });
        final Thread thread2 = new Thread(() -> {
            concurrentHashMapDuplicateRequestLimiter.tryRequest("key", 1);
        });
        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();
    }
}
