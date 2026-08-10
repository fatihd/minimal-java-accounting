package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.journal.JournalLine;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentJournalRepositoryTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);

    private ConcurrentJournalRepository repository;

    @BeforeEach
    void createRepository() {
        repository = new ConcurrentJournalRepository();
    }

    @Test
    void returnsDetachedEntriesAndLines() {
        JournalEntry entry = entry();
        JournalIdentifier identifier = repository.add(entry);

        JournalEntry first = repository.get(identifier).orElseThrow();
        first.setRevision(99L);
        JournalEntry second = repository.get(JournalIdentifier.request("request"))
                .orElseThrow();

        assertEquals(0L, second.getRevision());
        assertNotSame(first, second);
        assertNotSame(first.getLines().getFirst(), second.getLines().getFirst());
    }

    @Test
    void rejectsASecondEntryWithTheSameRequest() {
        repository.add(entry());

        assertThrows(IllegalStateException.class, () -> repository.add(entry()));
    }

    @Test
    void removingByRequestAlsoClearsItsLookup() {
        repository.add(entry());

        repository.remove(JournalIdentifier.request("request"));

        assertFalse(repository.get(JournalIdentifier.request("request")).isPresent());
    }

    private static JournalEntry entry() {
        return JournalEntry.create(
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(
                        new JournalLine(
                                1, DebitCredit.DEBIT, AMOUNT, AMOUNT,
                                new Money(1100, USD)),
                        new JournalLine(
                                2, DebitCredit.CREDIT, AMOUNT, AMOUNT,
                                new Money(900, USD))));
    }
}
