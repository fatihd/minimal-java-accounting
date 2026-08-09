package dev.minimalaccounting.adapters.messaging.artemis;

import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;

final class ArtemisSender<T> implements Sender<T> {
    private final ArtemisConnection connection;
    private final String address;
    private final JsonMessages<T> messages;

    ArtemisSender(
            ArtemisConnection connection,
            String address,
            Class<T> messageType) {
        this.connection = connection;
        this.address = address;
        this.messages = new JsonMessages<>(messageType);
    }

    @Override
    public void send(@NonNull T message) {
        try (var session = connection.session();
             var producer = session.createProducer(address)) {
            producer.send(session.createMessage(true)
                    .writeBodyBufferString(messages.write(message)));
        } catch (Exception failure) {
            throw new ArtemisFailure("send Artemis message", failure);
        }
    }
}
