package dev.minimalaccounting.adapters.persistence.inmemory;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public final class InMemoryRepository<I, T> {
    private final ConcurrentHashMap<I, T> values = new ConcurrentHashMap<>();
    @NonNull
    private final Function<T, I> newIdentifier;
    @NonNull
    private final Function<T, I> identifier;
    @NonNull
    private final BiConsumer<T, I> assignIdentifier;
    @NonNull
    private final Function<T, Long> revision;
    @NonNull
    private final BiConsumer<T, Long> assignRevision;
    @NonNull
    private final UnaryOperator<T> copy;

    public synchronized Optional<T> get(I identifier) {
        return Optional.ofNullable(values.get(identifier)).map(copy);
    }

    public synchronized I add(@NonNull T value) {
        I assignedIdentifier = Objects.requireNonNull(
                newIdentifier.apply(value), "identifier");
        if (values.containsKey(assignedIdentifier)) {
            throw new IllegalStateException("Value already exists: " + assignedIdentifier);
        }
        T snapshot = copy.apply(value);
        assignIdentifier.accept(snapshot, assignedIdentifier);
        assignRevision.accept(snapshot, 0L);
        values.put(assignedIdentifier, snapshot);
        assignIdentifier.accept(value, assignedIdentifier);
        assignRevision.accept(value, 0L);
        return assignedIdentifier;
    }

    public synchronized void update(@NonNull T value) {
        I valueIdentifier = Objects.requireNonNull(identifier.apply(value), "identifier");
        T current = values.get(valueIdentifier);
        if (current == null || !revision.apply(current).equals(revision.apply(value))) {
            throw new IllegalStateException("Stale or missing value: " + valueIdentifier);
        }
        long nextRevision = revision.apply(value) + 1;
        T snapshot = copy.apply(value);
        assignIdentifier.accept(snapshot, valueIdentifier);
        assignRevision.accept(snapshot, nextRevision);
        values.put(valueIdentifier, snapshot);
        assignRevision.accept(value, nextRevision);
    }

    public synchronized Optional<T> remove(I identifier) {
        return Optional.ofNullable(values.remove(identifier)).map(copy);
    }
}
