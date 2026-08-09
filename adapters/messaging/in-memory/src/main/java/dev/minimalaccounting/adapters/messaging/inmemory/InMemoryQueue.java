package dev.minimalaccounting.adapters.messaging.inmemory;

import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryQueue<T> implements Sender<T>, Receiver<T> {
    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentLinkedQueue<Stored<T>> available =
            new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Stored<T>> inFlight =
            new ConcurrentHashMap<>();

    @Override
    public synchronized void send(@NonNull T message) {
        String id = Long.toString(ids.incrementAndGet());
        available.add(new Stored<>(id, message));
    }

    @Override
    public synchronized List<Received<T>> receive(int limit) {
        if (limit < 1) {
            return List.of();
        }
        List<Received<T>> messages = new ArrayList<>(limit);
        for (int count = 0; count < limit; count++) {
            Stored<T> stored = available.poll();
            if (stored == null) {
                break;
            }
            inFlight.put(stored.messageId(), stored);
            messages.add(new Received<>(stored.messageId(), stored.message()));
        }
        return List.copyOf(messages);
    }

    @Override
    public synchronized void ack(String messageId) {
        if (inFlight.remove(messageId) == null) {
            throw unknown(messageId);
        }
    }

    @Override
    public synchronized void nack(String messageId) {
        Stored<T> stored = inFlight.remove(messageId);
        if (stored == null) {
            throw unknown(messageId);
        }
        available.add(stored);
    }

    public synchronized int size() {
        return available.size() + inFlight.size();
    }

    private static IllegalArgumentException unknown(String messageId) {
        return new IllegalArgumentException("Unknown messageId: " + messageId);
    }

    private record Stored<T>(String messageId, T message) {
    }
}
