package dev.minimalaccounting.infrastructure;

import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundaryDecoratorsTest {
    @Test
    void adaptsSingleArgumentPortsThroughDecoratedConsumers() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var decorators = new BoundaryDecorators(metrics);
        var submissions = new AtomicInteger();
        var handled = new AtomicReference<String>();
        var sent = new AtomicReference<String>();
        SubmitTransactionHandler submission = ignored -> submissions.incrementAndGet();

        decorators.handler("transaction.submit", submission)
                .handle(null);
        decorators.handler("command.handle", handled::set).handle("command");
        decorators.sender("event.send", sent::set).send("event");

        assertEquals(1, submissions.get());
        assertEquals("command", handled.get());
        assertEquals("event", sent.get());
        var operations = metrics.snapshot().operations();
        assertEquals(1, operations.get("transaction.submit").successes());
        assertEquals(1, operations.get("command.handle").successes());
        assertEquals(1, operations.get("event.send").successes());
    }

    @Test
    void receiverAdaptsPollingAndBothDispositions() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var delegate = new RecordingReceiver();
        var receiver = new BoundaryDecorators(metrics).receiver("queue", delegate);

        assertEquals(2, receiver.receive(2).size());
        receiver.ack("1");
        receiver.nack("2");

        assertEquals(2, delegate.limit);
        assertEquals("1", delegate.acked);
        assertEquals("2", delegate.nacked);
        var operations = metrics.snapshot().operations();
        assertEquals(2, operations.get("queue.receive").items());
        assertEquals(1, operations.get("queue.ack").successes());
        assertEquals(1, operations.get("queue.nack").successes());
    }

    @Test
    void receiverRethrowsTheSameDispositionFailure() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var expected = new IllegalArgumentException("unknown");
        Receiver<String> delegate = new RecordingReceiver() {
            @Override
            public void ack(String messageId) {
                throw expected;
            }
        };
        var receiver = new BoundaryDecorators(metrics).receiver("queue", delegate);

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> receiver.ack("missing"));

        assertSame(expected, thrown);
        assertEquals(1, metrics.snapshot().operations().get("queue.ack").failures());
    }

    private static class RecordingReceiver implements Receiver<String> {
        private int limit;
        private String acked;
        private String nacked;

        @Override
        public List<Received<String>> receive(int limit) {
            this.limit = limit;
            return List.of(new Received<>("1", "first"), new Received<>("2", "second"));
        }

        @Override
        public void ack(String messageId) {
            acked = messageId;
        }

        @Override
        public void nack(String messageId) {
            nacked = messageId;
        }
    }
}
