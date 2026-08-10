package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;

class JournalEntryCreationTest {
    private JournalEntry entry;

    @BeforeEach
    void createEntry() {
        Currency currency = Currency.getInstance("USD");
        Money amount = new Money(100, currency);
        entry = JournalEntry.create(
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(
                        new JournalLine(
                                1, DebitCredit.DEBIT, amount, amount,
                                new Money(1100, currency)),
                        new JournalLine(
                                2, DebitCredit.CREDIT, amount, amount,
                                new Money(900, currency))));
    }

    @Test
    void hasNoPersistenceIdentity() {
        assertNull(entry.getId());
    }

    @Test
    void hasNoPersistenceRevision() {
        assertNull(entry.getRevision());
    }
}
