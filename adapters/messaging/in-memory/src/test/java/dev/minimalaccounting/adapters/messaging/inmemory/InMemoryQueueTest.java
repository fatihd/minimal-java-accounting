package dev.minimalaccounting.adapters.messaging.inmemory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryQueueTest {
    @Test
    void ackRemovesReceivedMessage() {
        InMemoryQueue<String> queue = new InMemoryQueue<>();
        queue.send("message");
        var received = queue.receive(1).getFirst();

        queue.ack(received.messageId());

        assertEquals(0, queue.size());
    }

    @Test
    void nackMakesReceivedMessageAvailableAgain() {
        InMemoryQueue<String> queue = new InMemoryQueue<>();
        queue.send("message");
        var received = queue.receive(1).getFirst();

        queue.nack(received.messageId());

        assertEquals("message", queue.receive(1).getFirst().message());
    }

    @Test
    void ackRejectsUnknownMessageId() {
        InMemoryQueue<String> queue = new InMemoryQueue<>();

        assertThrows(IllegalArgumentException.class, () -> queue.ack("missing"));
    }

    @Test
    void nackRejectsUnknownMessageId() {
        InMemoryQueue<String> queue = new InMemoryQueue<>();

        assertThrows(IllegalArgumentException.class, () -> queue.nack("missing"));
    }
}
