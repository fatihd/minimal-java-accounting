package dev.minimalaccounting.shared.adapters.postgresql;

import lombok.NonNull;

public record PostgresqlConfiguration(
        @NonNull String jdbcUrl,
        @NonNull String user,
        @NonNull String password) {
    public PostgresqlConfiguration {
        if (jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be blank");
        }
        if (user.isBlank()) {
            throw new IllegalArgumentException("user must not be blank");
        }
    }
}
