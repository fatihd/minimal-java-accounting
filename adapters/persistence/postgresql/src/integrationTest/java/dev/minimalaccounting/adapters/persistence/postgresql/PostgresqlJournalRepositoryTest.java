package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.journal.JournalLine;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.AccountNumber;
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
    void rejectsDuplicateRequestsAndStaleUpdates() {
        JournalEntry entry = entry();
        repository.add(entry);
        JournalEntry stale = repository.get(JournalIdentifier.id(entry.getId())).orElseThrow();
        repository.update(entry);

        assertThrows(IllegalStateException.class, () -> repository.add(entry()));
        assertThrows(IllegalStateException.class, () -> repository.update(stale));
    }

    @Test
    void removesByRequest() {
        repository.add(entry());

        repository.remove(JournalIdentifier.request("request"));

        assertFalse(repository.get(JournalIdentifier.request("request")).isPresent());
    }

    private static JournalEntry entry() {
        return JournalEntry.create(
                "request", "TRANSFER", "document", "description",
                Instant.EPOCH, Instant.EPOCH,
                List.of(
                        new JournalLine(
                                new AccountNumber("DEBIT001"), DebitCredit.DEBIT,
                                AMOUNT, AMOUNT,
                                new Money(1100, AMOUNT.currency())),
                        new JournalLine(
                                new AccountNumber("CREDIT01"), DebitCredit.CREDIT,
                                AMOUNT, AMOUNT,
                                new Money(900, AMOUNT.currency()))));
    }
}
