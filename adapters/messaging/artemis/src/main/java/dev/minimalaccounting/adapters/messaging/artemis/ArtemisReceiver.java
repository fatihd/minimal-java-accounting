package dev.minimalaccounting.adapters.messaging.artemis;

import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class ArtemisReceiver<T> implements Receiver<T>, AutoCloseable {
    private final ArtemisConnection connection;
    private final String queue;
    private final JsonMessages<T> messages;
    private final ConcurrentHashMap<String, Delivery> inFlight = new ConcurrentHashMap<>();

    ArtemisReceiver(
            ArtemisConnection connection,
            String queue,
            Class<T> messageType) {
        this.connection = connection;
        this.queue = queue;
        this.messages = new JsonMessages<>(messageType);
    }

    @Override
    public List<Received<T>> receive(int limit) {
        if (limit < 1) {
            return List.of();
        }
        var received = new ArrayList<Received<T>>(limit);
        for (int count = 0; count < limit; count++) {
            Delivery delivery = receiveOne();
            if (delivery == null) {
                break;
            }
            String id = Long.toString(delivery.message().getMessageID());
            T message;
            try {
                message = messages.read(delivery.message().getBodyBuffer().readString());
            } catch (RuntimeException failure) {
                delivery.closeWithoutAcknowledgement();
                throw failure;
            }
            Delivery existing = inFlight.putIfAbsent(id, delivery);
            if (existing != null) {
                delivery.closeWithoutAcknowledgement();
                throw new IllegalStateException("Duplicate in-flight Artemis message: " + id);
            }
            received.add(new Received<>(id, message));
        }
        return List.copyOf(received);
    }

    @Override
    public void ack(String messageId) {
        Delivery delivery = remove(messageId);
        try {
            delivery.message().acknowledge();
        } catch (Exception failure) {
            throw new ArtemisFailure("acknowledge Artemis message", failure);
        } finally {
            delivery.close();
        }
    }

    @Override
    public void nack(String messageId) {
        Delivery delivery = remove(messageId);
        try {
            delivery.session().rollback();
        } catch (Exception failure) {
            throw new ArtemisFailure("reject Artemis message", failure);
        } finally {
            delivery.close();
        }
    }

    @Override
    public void close() {
        for (Delivery delivery : inFlight.values()) {
            delivery.closeWithoutAcknowledgement();
        }
        inFlight.clear();
    }

    private Delivery receiveOne() {
        ClientSession session = connection.session();
        ClientConsumer consumer = null;
        try {
            consumer = session.createConsumer(queue);
            session.start();
            ClientMessage message = consumer.receive(connection.receiveTimeoutMillis());
            if (message == null) {
                consumer.close();
                session.close();
                return null;
            }
            return new Delivery(session, consumer, message);
        } catch (Exception failure) {
            close(consumer);
            close(session);
            throw new ArtemisFailure("receive Artemis message", failure);
        }
    }

    private Delivery remove(String messageId) {
        Delivery delivery = inFlight.remove(messageId);
        if (delivery == null) {
            throw new IllegalArgumentException("Unknown messageId: " + messageId);
        }
        return delivery;
    }

    private static void close(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Preserve the original transport failure.
            }
        }
    }

    private record Delivery(
            ClientSession session,
            ClientConsumer consumer,
            ClientMessage message) {
        void close() {
            ArtemisReceiver.close(consumer);
            ArtemisReceiver.close(session);
        }

        void closeWithoutAcknowledgement() {
            try {
                session.rollback();
            } catch (Exception ignored) {
                // Closing the session still releases the delivery.
            }
            close();
        }
    }
}
