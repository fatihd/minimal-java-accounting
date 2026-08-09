package dev.minimalaccounting.ports.persistence;

import dev.minimalaccounting.core.journal.JournalEntry;
import dev.minimalaccounting.core.journal.JournalReference;

import java.util.Optional;

public interface JournalRepository {
    Optional<JournalEntry> get(JournalReference key);

    long add(JournalEntry value);

    void update(JournalEntry value);

    void remove(JournalReference key);
}
