package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.core.journal.JournalEntry;
import dev.minimalaccounting.core.journal.JournalLine;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresqlJournalRepositoryTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    private final PostgresqlTest database = new PostgresqlTest();
    private PostgresqlJournalRepository repository;

    @BeforeEach
    void createRepository() {
        database.resetDatabase();
        repository = new PostgresqlJournalRepository(database.transactions);
    }

    @Test
    void returnsDetachedEntriesAndLines() {
        JournalEntry entry = entry();
        long id = repository.add(entry);

        JournalEntry first = repository.get(JournalReference.id(id)).orElseThrow();
        first.setRevision(99L);
        JournalEntry second = repository.get(JournalReference.request("request"))
                .orElseThrow();

        assertEquals(0L, second.getRevision());
        assertNotSame(first, second);
        assertNotSame(first.getLines().getFirst(), second.getLines().getFirst());
    }

    @Test
    void rejectsDuplicateRequestsAndStaleUpdates() {
        JournalEntry entry = entry();
        repository.add(entry);
        JournalEntry stale = repository.get(JournalReference.id(entry.getId())).orElseThrow();
        repository.update(entry);

        assertThrows(IllegalStateException.class, () -> repository.add(entry()));
        assertThrows(IllegalStateException.class, () -> repository.update(stale));
    }

    @Test
    void removesByRequest() {
        repository.add(entry());

        repository.remove(JournalReference.request("request"));

        assertFalse(repository.get(JournalReference.request("request")).isPresent());
    }

    private static JournalEntry entry() {
        return JournalEntry.create(
                "request", "TRANSFER", "document", "description",
                Instant.EPOCH, Instant.EPOCH,
                List.of(
                        new JournalLine(
                                1, DebitCredit.DEBIT, AMOUNT, AMOUNT,
                                new Money(1100, AMOUNT.currency())),
                        new JournalLine(
                                2, DebitCredit.CREDIT, AMOUNT, AMOUNT,
                                new Money(900, AMOUNT.currency()))));
    }
}
