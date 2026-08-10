package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;

final class ArtemisSender<T> implements Sender<T> {
    private final ArtemisConnection connection;
    private final String address;

    ArtemisSender(ArtemisConnection connection, String address) {
        this.connection = connection;
        this.address = address;
    }

    @Override
    public void send(@NonNull T message) {
        try (var session = connection.session();
             var producer = session.createProducer(address)) {
            producer.send(session.createMessage(true)
                    .writeBodyBufferString(JsonMessages.write(message)));
        } catch (Exception failure) {
            throw new ArtemisFailure("send Artemis message", failure);
        }
    }
}
