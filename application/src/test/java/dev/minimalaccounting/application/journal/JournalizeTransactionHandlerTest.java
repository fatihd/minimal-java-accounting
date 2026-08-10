package dev.minimalaccounting.application.journal;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionLine;
import dev.minimalaccounting.model.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalizeTransactionHandlerTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Test
    void absentTransactionIsAcknowledgedAsStale() {
        List<TransactionJournalized> events = new ArrayList<>();
        JournalizeTransactionHandler handler = new JournalizeTransactionHandler(
                new ConcurrentAccountRepository(),
                new ConcurrentTransactionRepository(),
                new ConcurrentJournalRepository(),
                events::add,
                CLOCK);

        assertDoesNotThrow(() -> handler.handle(new JournalizeTransaction(99)));
        assertEquals(List.of(), events);
    }

    @Test
    void retryAfterPublicationFailureReusesThePersistedJournal() {
        Fixture fixture = fixture(true);
        List<TransactionJournalized> events = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        JournalizeTransactionHandler handler = new JournalizeTransactionHandler(
                fixture.accounts(),
                fixture.transactions(),
                fixture.journals(),
                event -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("topic unavailable");
                    }
                    events.add(event);
                },
                CLOCK);
        JournalizeTransaction command = new JournalizeTransaction(fixture.transactionId());

        assertThrows(IllegalStateException.class, () -> handler.handle(command));
        long journalId = fixture.journals().get(JournalIdentifier.request("request"))
                .orElseThrow()
                .getId();
        handler.handle(command);

        assertEquals(
                List.of(new TransactionJournalized(fixture.transactionId(), journalId)),
                events);
        assertEquals(
                journalId,
                fixture.journals().get(JournalIdentifier.request("request"))
                        .orElseThrow()
                        .getId());
    }

    @Test
    void missingAccountNacksWithoutCreatingAJournal() {
        Fixture fixture = fixture(false);
        List<TransactionJournalized> events = new ArrayList<>();
        JournalizeTransactionHandler handler = new JournalizeTransactionHandler(
                fixture.accounts(),
                fixture.transactions(),
                fixture.journals(),
                events::add,
                CLOCK);

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new JournalizeTransaction(fixture.transactionId())));
        assertTrue(fixture.journals().get(JournalIdentifier.request("request")).isEmpty());
        assertEquals(List.of(), events);
    }

    private static Fixture fixture(boolean includeCreditAccount) {
        ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();
        accounts.add(Account.create(
                new AccountNumber("DEBIT001"), "debit",
                new Money(1000, USD), null, null));
        if (includeCreditAccount) {
            accounts.add(Account.create(
                    new AccountNumber("CREDIT01"), "credit",
                    new Money(1000, USD), null, null));
        }
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction());
        return new Fixture(
                accounts,
                transactions,
                new ConcurrentJournalRepository(),
                transactionId);
    }

    private static Transaction transaction() {
        return new Transaction(
                null,
                null,
                TransactionState.HELD,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                List.of(
                        new TransactionLine(
                                1, new AccountNumber("DEBIT001"), DebitCredit.DEBIT,
                                AMOUNT, AMOUNT),
                        new TransactionLine(
                                2, new AccountNumber("CREDIT01"), DebitCredit.CREDIT,
                                AMOUNT, AMOUNT)),
                List.of());
    }

    private record Fixture(
            ConcurrentAccountRepository accounts,
            ConcurrentTransactionRepository transactions,
            ConcurrentJournalRepository journals,
            long transactionId) {
    }
}
