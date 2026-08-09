package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedMessagingTest {
    @Test
    void senderRecordsSuccessAndPreservesTheMessage() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var sent = new java.util.concurrent.atomic.AtomicReference<String>();
        var sender = new ObservedSender<String>(
                "sender", sent::set, metrics, System.getLogger("test"));

        sender.send("message");

        assertEquals("message", sent.get());
        assertEquals(1, metrics.snapshot().operations().get("sender").successes());
    }

    @Test
    void receiverCountsMessagesAndDispositions() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var delegate = new RecordingReceiver();
        var receiver = new ObservedReceiver<>(
                "queue", delegate, metrics, System.getLogger("test"));

        assertEquals(2, receiver.receive(2).size());
        receiver.ack("1");
        receiver.nack("2");

        var operations = metrics.snapshot().operations();
        assertEquals(2, operations.get("queue.receive").items());
        assertEquals("1", delegate.acked);
        assertEquals("2", delegate.nacked);
    }

    @Test
    void receiverRethrowsDispositionFailure() {
        var metrics = new MetricsRegistry(Clock.systemUTC());
        var expected = new IllegalArgumentException("unknown");
        Receiver<String> delegate = new RecordingReceiver() {
            @Override
            public void ack(String messageId) {
                throw expected;
            }
        };
        var receiver = new ObservedReceiver<>(
                "queue", delegate, metrics, System.getLogger("test"));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> receiver.ack("missing"));

        assertSame(expected, thrown);
        assertEquals(1, metrics.snapshot().operations().get("queue.ack").failures());
    }

    private static class RecordingReceiver implements Receiver<String> {
        private String acked;
        private String nacked;

        @Override
        public List<Received<String>> receive(int limit) {
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
