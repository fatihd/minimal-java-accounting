package dev.minimalaccounting.adapters.persistence.inmemory;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public final class InMemoryRepository<T> {
    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentHashMap<Long, T> values = new ConcurrentHashMap<>();
    @NonNull
    private final Function<T, Long> id;
    @NonNull
    private final BiConsumer<T, Long> assignId;
    @NonNull
    private final Function<T, Long> revision;
    @NonNull
    private final BiConsumer<T, Long> assignRevision;
    @NonNull
    private final UnaryOperator<T> copy;

    public synchronized Optional<T> get(long id) {
        return Optional.ofNullable(values.get(id)).map(copy);
    }

    public synchronized long add(@NonNull T value) {
        long assignedId = ids.incrementAndGet();
        T snapshot = copy.apply(value);
        assignId.accept(snapshot, assignedId);
        assignRevision.accept(snapshot, 0L);
        values.put(assignedId, snapshot);
        assignId.accept(value, assignedId);
        assignRevision.accept(value, 0L);
        return assignedId;
    }

    public synchronized void update(@NonNull T value) {
        Long valueId = Objects.requireNonNull(id.apply(value), "id");
        T current = values.get(valueId);
        if (current == null || !revision.apply(current).equals(revision.apply(value))) {
            throw new IllegalStateException("Stale or missing value: " + valueId);
        }
        long nextRevision = revision.apply(value) + 1;
        T snapshot = copy.apply(value);
        assignId.accept(snapshot, valueId);
        assignRevision.accept(snapshot, nextRevision);
        values.put(valueId, snapshot);
        assignRevision.accept(value, nextRevision);
    }

    public synchronized Optional<T> remove(long id) {
        return Optional.ofNullable(values.remove(id)).map(copy);
    }
}
