package dev.minimalaccounting.shared.ports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class TopicContract<T> {
    protected abstract Sender<T> sender();

    protected abstract Receiver<T> firstSubscription();

    protected abstract Receiver<T> secondSubscription();

    protected abstract T message(String value);

    @Test
    final void publishesToEachIndependentSubscription() {
        T expected = message("event");
        sender().send(expected);

        Received<T> first = firstSubscription().receive(1).getFirst();
        Received<T> second = secondSubscription().receive(1).getFirst();

        assertEquals(expected, first.message());
        assertEquals(expected, second.message());
        firstSubscription().ack(first.messageId());
        secondSubscription().ack(second.messageId());
    }
}
