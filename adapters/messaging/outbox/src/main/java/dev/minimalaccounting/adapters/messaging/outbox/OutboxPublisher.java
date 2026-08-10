package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import lombok.NonNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OutboxPublisher {
    private final JdbcTransactions transactions;
    private final Map<String, OutboxRoute<?>> routes;
    private final int batchSize;

    public OutboxPublisher(
            @NonNull JdbcTransactions transactions,
            @NonNull List<OutboxRoute<?>> routes,
            int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.transactions = transactions;
        this.routes = routes(routes);
        this.batchSize = batchSize;
    }

    public int publishOnce() {
        return transactions.inTransaction(() -> transactions.withRequiredConnection(
                connection -> publish(connection, batchSize, routes)));
    }

    private static int publish(
            Connection connection,
            int batchSize,
            Map<String, OutboxRoute<?>> routes) throws SQLException {
        List<Message> messages = lockMessages(connection, batchSize);
        for (Message message : messages) {
            OutboxRoute<?> route = routes.get(message.destination());
            if (route == null) {
                throw new IllegalStateException(
                        "No outbox route for destination: " + message.destination());
            }
            route.publish(message.payload());
            markPublished(connection, message.id());
        }
        return messages.size();
    }

    private static List<Message> lockMessages(Connection connection, int limit)
            throws SQLException {
        var messages = new ArrayList<Message>();
        try (var statement = connection.prepareStatement("""
                SELECT id, destination, payload::text
                FROM outbox_message
                WHERE published_at IS NULL
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """)) {
            statement.setInt(1, limit);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new Message(
                            result.getLong(1), result.getString(2), result.getString(3)));
                }
            }
        }
        return messages;
    }

    private static void markPublished(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE outbox_message SET published_at = CURRENT_TIMESTAMP WHERE id = ?
                """)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static Map<String, OutboxRoute<?>> routes(List<OutboxRoute<?>> routes) {
        var indexed = new HashMap<String, OutboxRoute<?>>();
        for (OutboxRoute<?> route : routes) {
            if (indexed.putIfAbsent(route.destination(), route) != null) {
                throw new IllegalArgumentException(
                        "Duplicate outbox destination: " + route.destination());
            }
        }
        return Map.copyOf(indexed);
    }

    private record Message(long id, String destination, String payload) {
    }
}
