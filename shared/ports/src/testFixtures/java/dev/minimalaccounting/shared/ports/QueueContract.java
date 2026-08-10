package dev.minimalaccounting.shared.ports;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class QueueContract<T> {
    protected abstract Sender<T> sender();

    protected abstract Receiver<T> receiver();

    protected abstract T message(String value);

    @Test
    final void sendsAndAcknowledgesMessages() {
        T expected = message("first");
        sender().send(expected);

        List<Received<T>> received = receiver().receive(1);

        assertEquals(1, received.size());
        assertEquals(expected, received.getFirst().message());
        receiver().ack(received.getFirst().messageId());
        assertTrue(receiver().receive(1).isEmpty());
    }

    @Test
    final void respectsTheReceiveLimit() {
        sender().send(message("first"));
        sender().send(message("second"));

        List<Received<T>> received = receiver().receive(1);

        assertEquals(1, received.size());
        receiver().ack(received.getFirst().messageId());
        List<Received<T>> remaining = receiver().receive(1);
        assertEquals(1, remaining.size());
        receiver().ack(remaining.getFirst().messageId());
    }

    @Test
    final void negativelyAcknowledgedMessagesAreRedelivered() {
        T expected = message("retry");
        sender().send(expected);
        Received<T> first = receiver().receive(1).getFirst();

        receiver().nack(first.messageId());

        Received<T> retried = receiver().receive(1).getFirst();
        assertEquals(expected, retried.message());
        receiver().ack(retried.messageId());
    }
}
