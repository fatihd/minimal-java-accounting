package dev.minimalaccounting.app;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerGroupTest {
    @Test
    void pollsUntilClosed() throws Exception {
        var polls = new AtomicInteger();
        var called = new CountDownLatch(2);

        try (var ignored = WorkerGroup.start(List.of(() -> {
            polls.incrementAndGet();
            called.countDown();
            return 0;
        }), Duration.ofMillis(1))) {
            assertTrue(called.await(2, TimeUnit.SECONDS));
        }

        int afterClose = polls.get();
        Thread.sleep(20);
        assertTrue(polls.get() <= afterClose + 1);
    }
}
