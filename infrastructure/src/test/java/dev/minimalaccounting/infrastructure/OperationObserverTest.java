package dev.minimalaccounting.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationObserverTest {
    @Test
    void consumerRecordsSuccessAndPreservesTheArgument() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var logger = new RecordingLogger();
        var received = new AtomicReference<String>();
        var consumer = new OperationObserver(metrics, logger)
                .consumer("handler", received::set);

        consumer.accept("message");

        assertEquals("message", received.get());
        assertEquals(1, metrics.snapshot().operations().get("handler").successes());
        assertEquals(List.of(System.Logger.Level.DEBUG), logger.levels);
    }

    @Test
    void consumerRecordsFailureAndRethrowsTheSameException() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var logger = new RecordingLogger();
        var expected = new IllegalStateException("failure");
        var consumer = new OperationObserver(metrics, logger)
                .consumer("handler", ignored -> {
                    throw expected;
                });

        var thrown = assertThrows(IllegalStateException.class,
                () -> consumer.accept("message"));

        assertSame(expected, thrown);
        assertEquals(1, metrics.snapshot().operations().get("handler").failures());
        assertEquals(List.of(System.Logger.Level.WARNING), logger.levels);
    }

    @Test
    void intFunctionPreservesTheResultAndCountsItsItems() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var logger = new RecordingLogger();
        var expected = List.of("first", "second");
        var function = new OperationObserver(metrics, logger)
                .intFunction("queue.receive", limit -> {
                    assertEquals(2, limit);
                    return expected;
                }, List::size);

        var result = function.apply(2);

        assertSame(expected, result);
        var snapshot = metrics.snapshot().operations().get("queue.receive");
        assertEquals(1, snapshot.successes());
        assertEquals(2, snapshot.items());
        assertEquals(List.of(System.Logger.Level.DEBUG), logger.levels);
    }

    private static final class RecordingLogger implements System.Logger {
        private final List<Level> levels = new ArrayList<>();

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(
                Level level,
                ResourceBundle bundle,
                String message,
                Throwable thrown) {
            levels.add(level);
        }

        @Override
        public void log(
                Level level,
                ResourceBundle bundle,
                String format,
                Object... parameters) {
            levels.add(level);
        }
    }
}
