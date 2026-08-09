package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.transaction.AccountEffect;
import dev.minimalaccounting.core.transaction.AccountEffectState;
import dev.minimalaccounting.core.transaction.Transaction;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.core.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountHoldPlacedHandlerTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void retryAfterJournalizationSendFailureReissuesTheCommand() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction());
        List<JournalizeTransaction> journalizations = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        AccountHoldPlacedHandler handler = new AccountHoldPlacedHandler(
                transactions,
                ignored -> { },
                command -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("queue unavailable");
                    }
                    journalizations.add(command);
                },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        AccountHoldPlaced event = new AccountHoldPlaced(1, transactionId, 0);

        assertThrows(IllegalStateException.class, () -> handler.handle(event));
        handler.handle(event);

        assertEquals(List.of(new JournalizeTransaction(transactionId)), journalizations);
        assertEquals(
                TransactionState.HELD,
                transactions.get(TransactionReference.id(transactionId))
                        .orElseThrow()
                        .getState());
    }

    private static Transaction transaction() {
        return new Transaction(
                null,
                null,
                TransactionState.PLACING_HOLDS,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                List.of(),
                List.of(new AccountEffect(
                        1,
                        new AccountNumber("ACCOUNT01"),
                        DebitCredit.DEBIT,
                        AMOUNT,
                        AccountEffectState.PENDING)));
    }
}
