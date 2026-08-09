package dev.minimalaccounting.ports.messaging;

@FunctionalInterface
public interface Handler<T> {
    void handle(T message);
}
