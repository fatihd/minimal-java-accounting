package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxTest {
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
        OutboxSchema.install(transactions);
    }

    @Test
    void storesInTheCurrentTransactionAndPublishesOnce() {
        var sender = new OutboxSender<Example>(transactions, "examples");
        var published = new ArrayList<Example>();
        var publisher = new OutboxPublisher(
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
        var sender = new OutboxSender<Example>(transactions, "examples");
        var published = new ArrayList<Example>();
        var publisher = new OutboxPublisher(
                transactions,
                List.of(new OutboxRoute<>("examples", Example.class, published::add)),
                20);
        assertThrows(IllegalStateException.class,
                () -> sender.send(new Example(1, "outside")));
        assertThrows(ExpectedFailure.class, () -> transactions.inTransaction(() -> {
            sender.send(new Example(2, "rolled back"));
            throw new ExpectedFailure();
        }));

        assertEquals(0, publisher.publishOnce());
        assertEquals(List.of(), published);
    }

    @Test
    void retriesAWholeBatchAfterPublicationFailure() {
        var sender = new OutboxSender<Example>(transactions, "examples");
        var fail = new AtomicBoolean(true);
        var published = new ArrayList<Example>();
        var publisher = new OutboxPublisher(
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
        assertThrows(IllegalArgumentException.class, () -> new OutboxPublisher(
                transactions,
                List.of(
                        new OutboxRoute<>("same", Example.class, ignored -> { }),
                        new OutboxRoute<>("same", Example.class, ignored -> { })),
                1));

        OutboxSchema.install(transactions);

        assertEquals(1, migrationCount());
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
