package dev.minimalaccounting.shared.ports;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public abstract class ExplicitUpdateRepositoryContract<T> {
    protected abstract T createValue();

    protected abstract long add(T value);

    protected abstract Optional<T> get(long id);

    protected abstract void update(T value);

    protected abstract void firstChange(T value);

    protected abstract void secondChange(T value);

    protected abstract Object stateOf(T value);

    @Test
    final void changingAnAddedValueDoesNotPersistIt() {
        T value = createValue();
        long id = add(value);
        Object storedState = stateOf(value);

        firstChange(value);

        assertEquals(storedState, stateOf(get(id).orElseThrow()));
    }

    @Test
    final void changingALoadedValueDoesNotPersistIt() {
        long id = add(createValue());
        T loaded = get(id).orElseThrow();
        Object storedState = stateOf(loaded);

        firstChange(loaded);

        assertEquals(storedState, stateOf(get(id).orElseThrow()));
    }

    @Test
    final void updateStoresTheSubmittedState() {
        long id = add(createValue());
        T changed = get(id).orElseThrow();
        firstChange(changed);

        update(changed);

        assertEquals(stateOf(changed), stateOf(get(id).orElseThrow()));
    }

    @Test
    final void changingAnUpdatedValueDoesNotPersistIt() {
        long id = add(createValue());
        T changed = get(id).orElseThrow();
        firstChange(changed);
        update(changed);
        Object storedState = stateOf(changed);

        secondChange(changed);

        assertEquals(storedState, stateOf(get(id).orElseThrow()));
    }

    @Test
    final void repeatedGetsReturnDetachedValues() {
        long id = add(createValue());
        T first = get(id).orElseThrow();
        T second = get(id).orElseThrow();
        Object storedState = stateOf(second);

        firstChange(first);

        assertNotSame(first, second);
        assertEquals(storedState, stateOf(second));
        assertEquals(storedState, stateOf(get(id).orElseThrow()));
    }
}
