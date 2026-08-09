package dev.minimalaccounting.adapters.messaging.inmemory;

import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTopic<T> implements Sender<T> {
    private final ConcurrentHashMap<String, InMemoryQueue<T>> subscriptions =
            new ConcurrentHashMap<>();

    public InMemoryQueue<T> subscribe(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Subscription name must not be blank");
        }
        return subscriptions.computeIfAbsent(name, ignored -> new InMemoryQueue<>());
    }

    @Override
    public void send(@NonNull T message) {
        for (InMemoryQueue<T> subscription : subscriptions.values()) {
            subscription.send(message);
        }
    }
}
