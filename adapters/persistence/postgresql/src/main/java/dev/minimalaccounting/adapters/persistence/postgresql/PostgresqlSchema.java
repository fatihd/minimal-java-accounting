package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlMigrations;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class PostgresqlSchema {
    private static final String MIGRATION = "/db/migration/V001__accounting.sql";

    private PostgresqlSchema() {
    }

    public static void install(@NonNull JdbcTransactions transactions) {
        PostgresqlMigrations.install(transactions, 1, "accounting", resource(MIGRATION));
    }

    private static String resource(String name) {
        try (var input = PostgresqlSchema.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing database migration: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read database migration: " + name, failure);
        }
    }

}
