package dev.minimalaccounting.adapters.messaging.postgresql;

import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlDataSourceFactory;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresqlOutboxTest {
    private static final PostgreSQLContainer<?> DATABASE =
            new PostgreSQLContainer<>("postgres:17.6-alpine")
                    .withUsername("outbox_test")
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
            }
            return null;
        });
        PostgresqlSchema.install(transactions);
        PostgresqlOutboxSchema.install(transactions);
    }

    @Test
    void storesInTheCurrentTransactionAndPublishesOnce() {
        var sender = new PostgresqlOutboxSender<Example>(transactions, "examples");
        var published = new ArrayList<Example>();
        var publisher = new PostgresqlOutboxPublisher(
                transactions,
                List.of(new OutboxRoute<>("examples", Example.class, published::add)),
                20);

        transactions.inTransaction(() -> sender.send(new Example(41, "stored")));

        assertEquals(1, publisher.publishOnce());
        assertEquals(List.of(new Example(41, "stored")), published);
        assertEquals(0, publisher.publishOnce());
    }

    @Test
    void refusesNonTransactionalSendsAndRollsBackFailedHandlers() {
        var sender = new PostgresqlOutboxSender<Example>(transactions, "examples");
        var published = new ArrayList<Example>();
        var publisher = new PostgresqlOutboxPublisher(
                transactions,
                List.of(new OutboxRoute<>("examples", Example.class, published::add)),
                20);
        var handler = new TransactionalHandler<Example>(transactions, message -> {
            sender.send(message);
            throw new ExpectedFailure();
        });

        assertThrows(IllegalStateException.class,
                () -> sender.send(new Example(1, "outside")));
        assertThrows(ExpectedFailure.class,
                () -> handler.handle(new Example(2, "rolled back")));

        assertEquals(0, publisher.publishOnce());
        assertEquals(List.of(), published);
    }

    @Test
    void retriesAWholeBatchAfterPublicationFailure() {
        var sender = new PostgresqlOutboxSender<Example>(transactions, "examples");
        var fail = new AtomicBoolean(true);
        var published = new ArrayList<Example>();
        var publisher = new PostgresqlOutboxPublisher(
                transactions,
                List.of(new OutboxRoute<>("examples", Example.class, message -> {
                    if (fail.getAndSet(false)) {
                        throw new ExpectedFailure();
                    }
                    published.add(message);
                })),
                20);
        transactions.inTransaction(() -> sender.send(new Example(3, "retry")));

        assertThrows(ExpectedFailure.class, publisher::publishOnce);
        assertEquals(1, publisher.publishOnce());
        assertEquals(List.of(new Example(3, "retry")), published);
    }

    @Test
    void validatesRoutesAndInstallsItsSchemaIdempotently() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlOutboxPublisher(
                transactions,
                List.of(
                        new OutboxRoute<>("same", Example.class, ignored -> { }),
                        new OutboxRoute<>("same", Example.class, ignored -> { })),
                1));

        PostgresqlOutboxSchema.install(transactions);

        assertEquals(2, migrationCount());
    }

    private long migrationCount() {
        return transactions.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT COUNT(*) FROM accounting_schema_history")) {
                result.next();
                return result.getLong(1);
            }
        });
    }

    private record Example(long aggregateId, String value) {
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
