package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxMessagingTest {
    private static final PostgreSQLContainer<?> DATABASE =
            new PostgreSQLContainer<>("postgres:17.6-alpine")
                    .withUsername("outbox_messaging_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withDatabaseName("accounting");

    static {
        DATABASE.start();
    }

    private JdbcTransactions transactions;
    private ArtemisConnection broker;

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
        broker = ArtemisTestBroker.connect();
    }

    @AfterEach
    void closeBroker() {
        broker.close();
    }

    @Test
    void stagesQueueMessagesBeforePublishingAndAcknowledgingThem() {
        var queue = new OutboxQueue<>(
                transactions, broker,
                ArtemisTestBroker.uniqueName("outbox.queue"), ExampleMessage.class);
        var publisher = new OutboxPublisher(
                transactions, List.of(queue.route()), 20);
        ExampleMessage expected = new ExampleMessage(41, "queued");

        transactions.inTransaction(() -> queue.send(expected));

        assertEquals(1, unpublished());
        assertEquals(1, publisher.publishOnce());
        var received = queue.receive(1).getFirst();
        assertEquals(expected, received.message());
        queue.ack(received.messageId());
        assertTrue(queue.receive(1).isEmpty());
        queue.close();
    }

    @Test
    void negativelyAcknowledgedQueueMessagesAreRedelivered() {
        var queue = new OutboxQueue<>(
                transactions, broker,
                ArtemisTestBroker.uniqueName("outbox.retry"), ExampleMessage.class);
        var publisher = new OutboxPublisher(
                transactions, List.of(queue.route()), 20);
        ExampleMessage expected = new ExampleMessage(42, "retry");
        transactions.inTransaction(() -> queue.send(expected));
        publisher.publishOnce();

        var first = queue.receive(1).getFirst();
        queue.nack(first.messageId());
        var retry = queue.receive(1).getFirst();

        assertEquals(expected, retry.message());
        queue.ack(retry.messageId());
        queue.close();
    }

    @Test
    void publishesTopicsToIndependentSubscriptions() throws Exception {
        var topic = new OutboxTopic<>(
                transactions, broker,
                ArtemisTestBroker.uniqueName("outbox.topic"), ExampleMessage.class);
        var first = topic.subscribe("first");
        var second = topic.subscribe("second");
        var publisher = new OutboxPublisher(
                transactions, List.of(topic.route()), 20);
        ExampleMessage expected = new ExampleMessage(43, "event");
        transactions.inTransaction(() -> topic.send(expected));

        publisher.publishOnce();

        var firstDelivery = first.receive(1).getFirst();
        var secondDelivery = second.receive(1).getFirst();
        assertEquals(expected, firstDelivery.message());
        assertEquals(expected, secondDelivery.message());
        first.ack(firstDelivery.messageId());
        second.ack(secondDelivery.messageId());
        ((AutoCloseable) first).close();
        ((AutoCloseable) second).close();
    }

    private long unpublished() {
        return transactions.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT COUNT(*) FROM outbox_message WHERE published_at IS NULL")) {
                result.next();
                return result.getLong(1);
            }
        });
    }
}
