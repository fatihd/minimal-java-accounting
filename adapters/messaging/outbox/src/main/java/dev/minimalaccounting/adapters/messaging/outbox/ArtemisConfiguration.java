package dev.minimalaccounting.adapters.messaging.outbox;

import lombok.NonNull;

public record ArtemisConfiguration(
        @NonNull String brokerUrl,
        @NonNull String username,
        @NonNull String password,
        long receiveTimeoutMillis) {
    public ArtemisConfiguration {
        if (brokerUrl.isBlank()) {
            throw new IllegalArgumentException("brokerUrl must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (receiveTimeoutMillis < 0) {
            throw new IllegalArgumentException("receiveTimeoutMillis must not be negative");
        }
    }
}
