package dev.minimalaccounting.observability;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedHandlerTest {
    @Test
    void recordsSuccessWithoutChangingTheMessage() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var handler = new ObservedHandler<String>(
                "handler", received::set, metrics, System.getLogger("test"));

        handler.handle("message");

        assertEquals("message", received.get());
        assertEquals(1, metrics.snapshot().operations().get("handler").successes());
    }

    @Test
    void recordsAndRethrowsTheSameRuntimeFailure() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var expected = new IllegalStateException("failure");
        var handler = new ObservedHandler<String>(
                "handler",
                ignored -> {
                    throw expected;
                },
                metrics,
                System.getLogger("test"));

        var thrown = assertThrows(IllegalStateException.class,
                () -> handler.handle("message"));

        assertSame(expected, thrown);
        assertEquals(1, metrics.snapshot().operations().get("handler").failures());
    }
}
