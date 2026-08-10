package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import org.apache.activemq.artemis.api.core.RoutingType;

public final class ArtemisTopic<T> implements Sender<T> {
    private final ArtemisConnection connection;
    private final String address;
    private final Class<T> messageType;
    private final ArtemisSender<T> sender;

    public ArtemisTopic(
            @NonNull ArtemisConnection connection,
            @NonNull String address,
            @NonNull Class<T> messageType) {
        if (address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        this.connection = connection;
        this.address = address;
        this.messageType = messageType;
        connection.ensureAddress(address, RoutingType.MULTICAST);
        sender = new ArtemisSender<>(connection, address);
    }

    public Receiver<T> subscribe(@NonNull String subscription) {
        if (subscription.isBlank()) {
            throw new IllegalArgumentException("subscription must not be blank");
        }
        String queue = address + "." + subscription;
        connection.ensureQueue(address, queue, RoutingType.MULTICAST);
        return new ArtemisReceiver<>(connection, queue, messageType);
    }

    @Override
    public void send(T message) {
        sender.send(message);
    }
}
