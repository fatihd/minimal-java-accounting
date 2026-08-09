package dev.minimalaccounting.app;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.concurrent.locks.LockSupport;

@RequiredArgsConstructor
final class WorkerGroup implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(WorkerGroup.class.getName());

    @NonNull
    private final AtomicBoolean running;
    @NonNull
    private final List<Thread> threads;

    static WorkerGroup start(
            @NonNull List<IntSupplier> pollers,
            @NonNull Duration idleDelay) {
        if (idleDelay.isNegative()) {
            throw new IllegalArgumentException("idleDelay must not be negative");
        }
        var running = new AtomicBoolean(true);
        var threads = new ArrayList<Thread>(pollers.size());
        var factory = Thread.ofVirtual().name("accounting-worker-", 0).factory();
        for (IntSupplier poller : pollers) {
            Thread thread = factory.newThread(
                    () -> poll(running, poller, idleDelay.toNanos()));
            threads.add(thread);
            thread.start();
        }
        return new WorkerGroup(running, List.copyOf(threads));
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        for (Thread thread : threads) {
            thread.interrupt();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void poll(
            AtomicBoolean running,
            IntSupplier poller,
            long idleDelayNanoseconds) {
        while (running.get()) {
            try {
                if (poller.getAsInt() == 0 && idleDelayNanoseconds > 0) {
                    LockSupport.parkNanos(idleDelayNanoseconds);
                }
            } catch (RuntimeException failure) {
                if (!running.get() && Thread.currentThread().isInterrupted()) {
                    return;
                }
                LOGGER.log(
                        System.Logger.Level.ERROR,
                        "Accounting worker failed",
                        failure);
                if (idleDelayNanoseconds > 0) {
                    LockSupport.parkNanos(idleDelayNanoseconds);
                }
            }
            if (Thread.interrupted() && !running.get()) {
                return;
            }
        }
    }
}
