package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.transaction.AccountEffect;
import dev.minimalaccounting.core.transaction.AccountEffectState;
import dev.minimalaccounting.core.transaction.Transaction;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.core.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountHoldReleasedHandlerTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void absentTransactionIsAcknowledgedAsStale() {
        AccountHoldReleasedHandler handler = new AccountHoldReleasedHandler(
                new ConcurrentTransactionRepository());

        assertDoesNotThrow(
                () -> handler.handle(new AccountHoldReleased(1, 99, 0)));
    }

    @Test
    void prematureReleaseCompletionNacksWithoutChangingState() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction(
                TransactionState.PLACING_HOLDS,
                effect(AccountEffectState.HELD)));
        AccountHoldReleasedHandler handler = new AccountHoldReleasedHandler(transactions);

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new AccountHoldReleased(1, transactionId, 0)));
        assertEquals(
                AccountEffectState.HELD,
                transactions.get(TransactionReference.id(transactionId))
                        .orElseThrow()
                        .getEffects()
                        .getFirst()
                        .getState());
    }

    @Test
    void duplicateReleaseCompletionIsIdempotent() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction(
                TransactionState.RELEASING_HOLDS,
                effect(AccountEffectState.RELEASING)));
        AccountHoldReleasedHandler handler = new AccountHoldReleasedHandler(transactions);
        AccountHoldReleased event = new AccountHoldReleased(1, transactionId, 0);

        handler.handle(event);
        handler.handle(event);

        assertEquals(
                TransactionState.REJECTED,
                transactions.get(TransactionReference.id(transactionId))
                        .orElseThrow()
                        .getState());
    }

    private static Transaction transaction(
            TransactionState state,
            AccountEffect effect) {
        return new Transaction(
                null, null, state, "request", "TRANSFER", "document", "description",
                Instant.EPOCH, Instant.EPOCH, null, null, List.of(), List.of(effect));
    }

    private static AccountEffect effect(AccountEffectState state) {
        return new AccountEffect(
                1, new AccountNumber("ACCOUNT01"), DebitCredit.DEBIT, AMOUNT, state);
    }
}
