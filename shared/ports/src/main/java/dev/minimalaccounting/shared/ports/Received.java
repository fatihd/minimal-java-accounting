package dev.minimalaccounting.shared.ports;

import lombok.NonNull;

public record Received<T>(String messageId, @NonNull T message) {
    public Received {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
    }
}
