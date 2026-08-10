package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlMigrations;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OutboxSchema {
    private static final String MIGRATION = "/db/migration/V002__outbox.sql";

    private OutboxSchema() {
    }

    public static void install(@NonNull JdbcTransactions transactions) {
        PostgresqlMigrations.install(transactions, 2, "outbox", resource());
    }

    private static String resource() {
        try (var input = OutboxSchema.class.getResourceAsStream(MIGRATION)) {
            if (input == null) {
                throw new IllegalStateException("Missing database migration: " + MIGRATION);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read database migration: " + MIGRATION,
                    failure);
        }
    }

}
