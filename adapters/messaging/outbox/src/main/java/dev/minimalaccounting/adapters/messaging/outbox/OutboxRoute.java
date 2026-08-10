package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;

public record OutboxRoute<T>(
        @NonNull String destination,
        @NonNull Class<T> messageType,
        @NonNull Sender<T> sender) {
    public OutboxRoute {
        if (destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
    }

    void publish(String payload) {
        sender.send(JsonMessages.read(payload, messageType));
    }
}
