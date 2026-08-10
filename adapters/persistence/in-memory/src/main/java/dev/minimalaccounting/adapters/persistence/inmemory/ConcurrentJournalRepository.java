package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.journal.JournalLine;
import dev.minimalaccounting.model.journal.JournalIdentifier;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ConcurrentJournalRepository implements Repository<JournalIdentifier, JournalEntry> {
    private final AtomicLong ids = new AtomicLong();
    private final InMemoryRepository<Long, JournalEntry> values = new InMemoryRepository<>(
            ignored -> ids.incrementAndGet(),
            JournalEntry::getId,
            JournalEntry::setId,
            JournalEntry::getRevision,
            JournalEntry::setRevision,
            ConcurrentJournalRepository::copy);
    private final ConcurrentHashMap<String, Long> requests = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<JournalEntry> get(JournalIdentifier key) {
        Long id = key.id() != null ? key.id() : requests.get(key.requestId());
        return id == null ? Optional.empty() : values.get(id);
    }

    @Override
    public synchronized JournalIdentifier add(JournalEntry value) {
        if (requests.containsKey(value.getRequestId())) {
            throw new IllegalStateException("Journal entry already exists: " + value.getRequestId());
        }
        long id = values.add(value);
        requests.put(value.getRequestId(), id);
        return JournalIdentifier.id(id);
    }

    @Override
    public synchronized void update(JournalEntry value) {
        values.update(value);
    }

    @Override
    public synchronized void remove(JournalIdentifier key) {
        Long id = key.id() != null ? key.id() : requests.get(key.requestId());
        if (id != null) {
            values.remove(id).ifPresent(value -> requests.remove(value.getRequestId()));
        }
    }

    private static JournalEntry copy(JournalEntry value) {
        ArrayList<JournalLine> lines = new ArrayList<>();
        for (JournalLine line : value.getLines()) {
            lines.add(new JournalLine(
                    line.getAccountNumber(), line.getSide(), line.getTransactionAmount(),
                    line.getLedgerAmount(), line.getBalanceAfter()));
        }
        return new JournalEntry(
                value.getId(), value.getRevision(), value.getRequestId(), value.getDocumentType(),
                value.getDocumentNumber(), value.getDescription(), value.getOccurredAt(),
                value.getJournalizedAt(), lines);
    }
}
