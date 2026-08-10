package dev.minimalaccounting.shared.ports;

@FunctionalInterface
public interface Sender<T> {
    void send(T message);
}
