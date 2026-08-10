package dev.minimalaccounting.shared.ports;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public abstract class ExplicitUpdateRepositoryContract<I, T> {
    protected abstract T createValue();

    protected abstract I add(T value);

    protected abstract Optional<T> get(I identifier);

    protected abstract void update(T value);

    protected abstract void firstChange(T value);

    protected abstract void secondChange(T value);

    protected abstract Object stateOf(T value);

    @Test
    final void changingAnAddedValueDoesNotPersistIt() {
        T value = createValue();
        I identifier = add(value);
        Object storedState = stateOf(value);

        firstChange(value);

        assertEquals(storedState, stateOf(get(identifier).orElseThrow()));
    }

    @Test
    final void changingALoadedValueDoesNotPersistIt() {
        I identifier = add(createValue());
        T loaded = get(identifier).orElseThrow();
        Object storedState = stateOf(loaded);

        firstChange(loaded);

        assertEquals(storedState, stateOf(get(identifier).orElseThrow()));
    }

    @Test
    final void updateStoresTheSubmittedState() {
        I identifier = add(createValue());
        T changed = get(identifier).orElseThrow();
        firstChange(changed);

        update(changed);

        assertEquals(stateOf(changed), stateOf(get(identifier).orElseThrow()));
    }

    @Test
    final void changingAnUpdatedValueDoesNotPersistIt() {
        I identifier = add(createValue());
        T changed = get(identifier).orElseThrow();
        firstChange(changed);
        update(changed);
        Object storedState = stateOf(changed);

        secondChange(changed);

        assertEquals(storedState, stateOf(get(identifier).orElseThrow()));
    }

    @Test
    final void repeatedGetsReturnDetachedValues() {
        I identifier = add(createValue());
        T first = get(identifier).orElseThrow();
        T second = get(identifier).orElseThrow();
        Object storedState = stateOf(second);

        firstChange(first);

        assertNotSame(first, second);
        assertEquals(storedState, stateOf(second));
        assertEquals(storedState, stateOf(get(identifier).orElseThrow()));
    }
}
