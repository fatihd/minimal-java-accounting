package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;
import lombok.NonNull;

public final class OutboxTopic<T> implements Sender<T> {
    private final String destination;
    private final Class<T> messageType;
    private final OutboxSender<T> outbox;
    private final ArtemisTopic<T> broker;

    public OutboxTopic(
            @NonNull JdbcTransactions transactions,
            @NonNull ArtemisConnection connection,
            @NonNull String destination,
            @NonNull Class<T> messageType) {
        this.destination = destination;
        this.messageType = messageType;
        outbox = new OutboxSender<>(transactions, destination);
        broker = new ArtemisTopic<>(connection, destination, messageType);
    }

    @Override
    public void send(T message) {
        outbox.send(message);
    }

    public Receiver<T> subscribe(@NonNull String subscription) {
        return broker.subscribe(subscription);
    }

    public OutboxRoute<T> route() {
        return new OutboxRoute<>(destination, messageType, broker);
    }
}
