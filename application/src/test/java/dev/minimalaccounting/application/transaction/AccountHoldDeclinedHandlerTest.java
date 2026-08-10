package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.transaction.AccountEffect;
import dev.minimalaccounting.model.transaction.AccountEffectState;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountHoldDeclinedHandlerTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void retryAfterReleaseSendFailureReissuesOutstandingCommands() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction()).id();
        List<ReleaseAccountHold> releases = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        AccountHoldDeclinedHandler handler = new AccountHoldDeclinedHandler(
                transactions,
                command -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("queue unavailable");
                    }
                    releases.add(command);
                });
        AccountHoldDeclined event = new AccountHoldDeclined(
                new AccountNumber("ACCOUNT01"), transactionId, 0, "limit exceeded");

        assertThrows(IllegalStateException.class, () -> handler.handle(event));
        handler.handle(event);

        assertEquals(
                List.of(new ReleaseAccountHold(
                        new AccountNumber("ACCOUNT02"), transactionId, 1,
                        DebitCredit.DEBIT, AMOUNT)),
                releases);
        Transaction stored = transactions.get(TransactionIdentifier.id(transactionId))
                .orElseThrow();
        assertEquals(TransactionState.RELEASING_HOLDS, stored.getState());
        assertEquals("limit exceeded", stored.getFailureReason());
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
                List.of(
                        effect(1, AccountEffectState.PENDING),
                        effect(2, AccountEffectState.HELD)));
    }

    private static AccountEffect effect(long accountId, AccountEffectState state) {
        return new AccountEffect(
                new AccountNumber("ACCOUNT0" + accountId),
                DebitCredit.DEBIT,
                AMOUNT,
                state);
    }
}
