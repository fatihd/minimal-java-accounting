package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.api.TransactionProcessor;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedTransactionProcessorTest {
    @Test
    void recordsAndRethrowsSubmissionFailure() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var expected = new IllegalArgumentException("invalid");
        TransactionProcessor delegate = ignored -> {
            throw expected;
        };
        var processor = new ObservedTransactionProcessor(
                "submit", delegate, metrics, System.getLogger("test"));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> processor.submit(null));

        assertSame(expected, thrown);
        assertEquals(1, metrics.snapshot().operations().get("submit").failures());
    }
}
