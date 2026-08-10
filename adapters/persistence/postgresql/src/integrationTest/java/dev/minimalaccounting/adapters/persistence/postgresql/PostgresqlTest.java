package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlDataSourceFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

final class PostgresqlTest {
    private static final PostgreSQLContainer<?> DATABASE =
            new PostgreSQLContainer<>("postgres:17.6-alpine")
                    .withUsername("accounting_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withDatabaseName("accounting");

    static {
        DATABASE.start();
    }

    protected JdbcTransactions transactions;

    void resetDatabase() {
        transactions = new JdbcTransactions(PostgresqlDataSourceFactory.create(
                new PostgresqlConfiguration(
                        DATABASE.getJdbcUrl(),
                        DATABASE.getUsername(),
                        DATABASE.getPassword())));
        transactions.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA public CASCADE");
                statement.execute("CREATE SCHEMA public");
            }
            return null;
        });
        PostgresqlSchema.install(transactions);
    }
}
