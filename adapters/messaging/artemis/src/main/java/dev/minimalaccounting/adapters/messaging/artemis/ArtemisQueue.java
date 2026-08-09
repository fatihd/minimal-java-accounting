package dev.minimalaccounting.adapters.messaging.artemis;

import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import org.apache.activemq.artemis.api.core.RoutingType;

import java.util.List;

public final class ArtemisQueue<T> implements Sender<T>, Receiver<T>, AutoCloseable {
    private final ArtemisSender<T> sender;
    private final ArtemisReceiver<T> receiver;

    public ArtemisQueue(
            @NonNull ArtemisConnection connection,
            @NonNull String name,
            @NonNull Class<T> messageType) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        connection.ensureQueue(name, name, RoutingType.ANYCAST);
        sender = new ArtemisSender<>(connection, name, messageType);
        receiver = new ArtemisReceiver<>(connection, name, messageType);
    }

    @Override
    public void send(T message) {
        sender.send(message);
    }

    @Override
    public List<Received<T>> receive(int limit) {
        return receiver.receive(limit);
    }

    @Override
    public void ack(String messageId) {
        receiver.ack(messageId);
    }

    @Override
    public void nack(String messageId) {
        receiver.nack(messageId);
    }

    @Override
    public void close() {
        receiver.close();
    }
}
