package dev.minimalaccounting.observability;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

@RequiredArgsConstructor
public final class MetricsRegistry {
    private static final long[] HISTOGRAM_BOUNDS_NANOS = {
            1_000_000L,
            5_000_000L,
            10_000_000L,
            25_000_000L,
            50_000_000L,
            100_000_000L,
            250_000_000L,
            500_000_000L,
            1_000_000_000L,
            5_000_000_000L,
            Long.MAX_VALUE
    };

    @NonNull
    private final Clock clock;
    private final ConcurrentHashMap<String, Measurement> operations =
            new ConcurrentHashMap<>();

    public MetricsSnapshot snapshot() {
        Map<String, OperationSnapshot> values = new LinkedHashMap<>();
        for (var entry : new TreeMap<>(operations).entrySet()) {
            values.put(entry.getKey(), entry.getValue().snapshot());
        }
        return new MetricsSnapshot(clock.instant(), values);
    }

    void success(String name, long elapsedNanoseconds, long items) {
        operations.computeIfAbsent(name, ignored -> new Measurement())
                .record(true, elapsedNanoseconds, items);
    }

    void failure(String name, long elapsedNanoseconds) {
        operations.computeIfAbsent(name, ignored -> new Measurement())
                .record(false, elapsedNanoseconds, 0);
    }

    private static final class Measurement {
        private final LongAdder attempts = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder items = new LongAdder();
        private final LongAdder totalNanoseconds = new LongAdder();
        private final AtomicLongArray histogram =
                new AtomicLongArray(HISTOGRAM_BOUNDS_NANOS.length);

        private void record(boolean succeeded, long elapsedNanoseconds, long itemCount) {
            attempts.increment();
            if (succeeded) {
                successes.increment();
                items.add(itemCount);
            } else {
                failures.increment();
            }
            totalNanoseconds.add(elapsedNanoseconds);
            for (int index = 0; index < HISTOGRAM_BOUNDS_NANOS.length; index++) {
                if (elapsedNanoseconds <= HISTOGRAM_BOUNDS_NANOS[index]) {
                    histogram.incrementAndGet(index);
                    return;
                }
            }
        }

        private OperationSnapshot snapshot() {
            List<Long> bounds = new ArrayList<>(HISTOGRAM_BOUNDS_NANOS.length);
            List<Long> counts = new ArrayList<>(HISTOGRAM_BOUNDS_NANOS.length);
            for (int index = 0; index < HISTOGRAM_BOUNDS_NANOS.length; index++) {
                bounds.add(HISTOGRAM_BOUNDS_NANOS[index]);
                counts.add(histogram.get(index));
            }
            return new OperationSnapshot(
                    attempts.sum(),
                    successes.sum(),
                    failures.sum(),
                    items.sum(),
                    totalNanoseconds.sum(),
                    bounds,
                    counts);
        }
    }
}
