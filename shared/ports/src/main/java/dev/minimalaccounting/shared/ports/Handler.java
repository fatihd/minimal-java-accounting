package dev.minimalaccounting.shared.ports;

@FunctionalInterface
public interface Handler<T> {
    void handle(T message);
}
