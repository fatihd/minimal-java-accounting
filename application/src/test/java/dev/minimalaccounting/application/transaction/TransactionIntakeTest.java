package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionIntakeTest {
    private ConcurrentTransactionRepository transactions;
    private TransactionIntake intake;

    @BeforeEach
    void createIntake() {
        transactions = new ConcurrentTransactionRepository();
        intake = new TransactionIntake(
                new ConcurrentAccountRepository(),
                transactions,
                new ConcurrentJournalRepository(),
                ignored -> { },
                ignored -> { },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void rejectsMissingAccount() {
        assertThrows(IllegalArgumentException.class, () -> intake.handle(transaction()));
    }

    @Test
    void doesNotPersistTransactionWithMissingAccount() {
        try {
            intake.handle(transaction());
        } catch (IllegalArgumentException ignored) {
            // Expected; persistence is asserted independently.
        }

        assertTrue(transactions.get(TransactionIdentifier.request("request")).isEmpty());
    }

    private static SubmitTransaction transaction() {
        Money amount = new Money(100, Currency.getInstance("USD"));
        return new SubmitTransaction(
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                List.of(new SubmitTransaction.Line(
                        new AccountNumber("MISSING1"),
                        DebitCredit.DEBIT,
                        amount,
                        amount)));
    }
}
