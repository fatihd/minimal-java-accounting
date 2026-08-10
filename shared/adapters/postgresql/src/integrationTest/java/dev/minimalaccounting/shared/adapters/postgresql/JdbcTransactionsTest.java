package dev.minimalaccounting.shared.adapters.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcTransactionsTest {
    private static final PostgreSQLContainer<?> DATABASE =
            new PostgreSQLContainer<>("postgres:17.6-alpine")
                    .withUsername("shared_postgresql_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withDatabaseName("accounting");

    static {
        DATABASE.start();
    }

    private JdbcTransactions transactions;

    @BeforeEach
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
                statement.execute("CREATE TABLE example(id INTEGER PRIMARY KEY)");
            }
            return null;
        });
    }

    @Test
    void commitsSuccessfulWorkAndRollsBackFailures() {
        transactions.inTransaction(() -> insert(1));

        assertThrows(ExpectedFailure.class, () -> transactions.inTransaction(() -> {
            insert(2);
            throw new ExpectedFailure();
        }));

        assertEquals(1, count("example"));
    }

    @Test
    void nestedWorkUsesTheCurrentConnection() {
        transactions.inTransaction(() -> {
            var connection = transactions.requiredConnection();
            transactions.inTransaction(() -> {
                assertSame(connection, transactions.requiredConnection());
                insert(1);
            });
            return null;
        });

        assertEquals(1, count("example"));
    }

    @Test
    void requiresAnActiveTransactionWhenRequested() {
        assertThrows(IllegalStateException.class, transactions::requiredConnection);
        assertThrows(IllegalStateException.class,
                () -> transactions.withRequiredConnection(connection -> null));
    }

    @Test
    void translatesSqlFailures() {
        PostgresqlFailure failure = assertThrows(PostgresqlFailure.class,
                () -> transactions.withConnection(connection -> {
                    try (var statement = connection.createStatement()) {
                        statement.execute("SELECT * FROM missing_table");
                    }
                    return null;
                }));

        assertEquals("42P01", failure.sqlState());
    }

    @Test
    void installsMigrationsIdempotentlyAndInEitherOrder() {
        PostgresqlMigrations.install(
                transactions, 2, "second", "CREATE TABLE second_value(id INTEGER)");
        PostgresqlMigrations.install(
                transactions, 1, "first", "CREATE TABLE first_value(id INTEGER)");
        PostgresqlMigrations.install(
                transactions, 2, "second", "CREATE TABLE second_value(id INTEGER)");

        assertEquals(2, count("accounting_schema_history"));
        assertEquals(0, count("first_value"));
        assertEquals(0, count("second_value"));
    }

    @Test
    void rejectsChangedAndInvalidMigrations() {
        PostgresqlMigrations.install(
                transactions, 1, "first", "CREATE TABLE first_value(id INTEGER)");

        assertThrows(IllegalStateException.class, () -> PostgresqlMigrations.install(
                transactions, 1, "first", "CREATE TABLE changed_value(id INTEGER)"));
        assertThrows(IllegalArgumentException.class, () -> PostgresqlMigrations.install(
                transactions, 0, "invalid", "SELECT 1"));
        assertThrows(IllegalArgumentException.class, () -> PostgresqlMigrations.install(
                transactions, 2, " ", "SELECT 1"));
    }

    private void insert(int id) {
        transactions.withRequiredConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO example(id) VALUES (?)")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private long count(String table) {
        return transactions.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                result.next();
                return result.getLong(1);
            }
        });
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
