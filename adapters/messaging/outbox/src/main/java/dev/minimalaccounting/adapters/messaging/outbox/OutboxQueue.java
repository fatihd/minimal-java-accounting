package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.ports.Received;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;
import lombok.NonNull;

import java.util.List;

public final class OutboxQueue<T> implements Sender<T>, Receiver<T>, AutoCloseable {
    private final String destination;
    private final Class<T> messageType;
    private final OutboxSender<T> outbox;
    private final ArtemisQueue<T> broker;

    public OutboxQueue(
            @NonNull JdbcTransactions transactions,
            @NonNull ArtemisConnection connection,
            @NonNull String destination,
            @NonNull Class<T> messageType) {
        this.destination = destination;
        this.messageType = messageType;
        outbox = new OutboxSender<>(transactions, destination);
        broker = new ArtemisQueue<>(connection, destination, messageType);
    }

    @Override
    public void send(T message) {
        outbox.send(message);
    }

    @Override
    public List<Received<T>> receive(int limit) {
        return broker.receive(limit);
    }

    @Override
    public void ack(String messageId) {
        broker.ack(messageId);
    }

    @Override
    public void nack(String messageId) {
        broker.nack(messageId);
    }

    public OutboxRoute<T> route() {
        return new OutboxRoute<>(destination, messageType, broker);
    }

    @Override
    public void close() {
        broker.close();
    }
}
