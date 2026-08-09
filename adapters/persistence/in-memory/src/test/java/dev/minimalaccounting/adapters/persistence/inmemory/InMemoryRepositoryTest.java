package dev.minimalaccounting.adapters.persistence.inmemory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryRepositoryTest {
    private InMemoryRepository<Value> repository;

    @BeforeEach
    void createRepository() {
        repository = new InMemoryRepository<>(
                Value::id,
                Value::setId,
                Value::revision,
                Value::setRevision,
                Value::copy);
    }

    @Test
    void addAssignsIdentityAndRevisionAndStoresASnapshot() {
        Value value = new Value("initial");

        long id = repository.add(value);
        value.setState("changed");
        Value stored = repository.get(id).orElseThrow();

        assertEquals(1L, id);
        assertEquals(1L, value.id());
        assertEquals(0L, value.revision());
        assertEquals("initial", stored.state());
        assertNotSame(value, stored);
    }

    @Test
    void getReturnsADetachedSnapshot() {
        long id = repository.add(new Value("initial"));

        Value first = repository.get(id).orElseThrow();
        first.setState("changed");
        Value second = repository.get(id).orElseThrow();

        assertEquals("initial", second.state());
        assertNotSame(first, second);
    }

    @Test
    void updateAdvancesTheRevisionAndStoresASnapshot() {
        Value value = new Value("initial");
        long id = repository.add(value);
        value.setState("updated");

        repository.update(value);
        value.setState("changed again");
        Value stored = repository.get(id).orElseThrow();

        assertEquals(1L, value.revision());
        assertEquals(1L, stored.revision());
        assertEquals("updated", stored.state());
    }

    @Test
    void staleUpdateFailsWithoutChangingStoredState() {
        long id = repository.add(new Value("initial"));
        Value stale = repository.get(id).orElseThrow();
        Value current = repository.get(id).orElseThrow();
        current.setState("current");
        repository.update(current);
        stale.setState("stale");

        assertThrows(IllegalStateException.class, () -> repository.update(stale));
        assertEquals("current", repository.get(id).orElseThrow().state());
    }

    @Test
    void updateRejectsAMissingValue() {
        Value missing = new Value("missing");
        missing.setId(42L);
        missing.setRevision(0L);

        assertThrows(IllegalStateException.class, () -> repository.update(missing));
    }

    @Test
    void removeReturnsADetachedSnapshotAndDeletesTheValue() {
        long id = repository.add(new Value("initial"));

        Value removed = repository.remove(id).orElseThrow();
        removed.setState("changed");

        assertEquals("changed", removed.state());
        assertFalse(repository.get(id).isPresent());
        assertFalse(repository.remove(id).isPresent());
    }

    private static final class Value {
        private Long id;
        private Long revision;
        private String state;

        private Value(String state) {
            this.state = state;
        }

        private Value(Long id, Long revision, String state) {
            this.id = id;
            this.revision = revision;
            this.state = state;
        }

        private Long id() {
            return id;
        }

        private void setId(Long id) {
            this.id = id;
        }

        private Long revision() {
            return revision;
        }

        private void setRevision(Long revision) {
            this.revision = revision;
        }

        private String state() {
            return state;
        }

        private void setState(String state) {
            this.state = state;
        }

        private Value copy() {
            return new Value(id, revision, state);
        }
    }
}
