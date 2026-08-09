package dev.minimalaccounting.ports.messaging;

@FunctionalInterface
public interface Sender<T> {
    void send(T message);
}
