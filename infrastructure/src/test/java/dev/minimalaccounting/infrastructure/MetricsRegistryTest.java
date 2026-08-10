package dev.minimalaccounting.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsRegistryTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void snapshotsSuccessfulAndFailedOperationsInNameOrder() {
        var metrics = new MetricsRegistry(Clock.fixed(NOW, ZoneOffset.UTC));

        metrics.success("second", 6_000_000, 3);
        metrics.failure("first", 2_000_000);

        var snapshot = metrics.snapshot();
        assertEquals(NOW, snapshot.capturedAt());
        assertEquals(java.util.List.of("first", "second"),
                snapshot.operations().keySet().stream().toList());
        assertEquals(1, snapshot.operations().get("first").failures());
        assertEquals(1, snapshot.operations().get("second").successes());
        assertEquals(3, snapshot.operations().get("second").items());
        assertEquals(1L, snapshot.operations().get("second").histogramCounts().get(2));
    }
}
