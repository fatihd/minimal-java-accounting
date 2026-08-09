package dev.minimalaccounting.adapters.messaging.postgresql;

import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PostgresqlOutboxSender<T> implements Sender<T> {
    @NonNull
    private final JdbcTransactions transactions;
    @NonNull
    private final String destination;

    @Override
    public void send(@NonNull T message) {
        if (destination.isBlank()) {
            throw new IllegalStateException("destination must not be blank");
        }
        transactions.withRequiredConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO outbox_message(destination, payload)
                    VALUES (?, CAST(? AS JSONB))
                    """)) {
                statement.setString(1, destination);
                statement.setString(2, JsonMessages.write(message));
                statement.executeUpdate();
            }
            return null;
        });
    }
}
