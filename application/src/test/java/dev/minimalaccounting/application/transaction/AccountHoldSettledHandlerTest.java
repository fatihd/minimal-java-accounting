package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.core.account.AccountHoldSettled;
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

class AccountHoldSettledHandlerTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void absentTransactionIsAcknowledgedAsStale() {
        AccountHoldSettledHandler handler = new AccountHoldSettledHandler(
                new ConcurrentTransactionRepository());

        assertDoesNotThrow(
                () -> handler.handle(new AccountHoldSettled(1, 99, 0)));
    }

    @Test
    void prematureSettlementCompletionNacksWithoutChangingState() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD)));
        AccountHoldSettledHandler handler = new AccountHoldSettledHandler(transactions);

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new AccountHoldSettled(1, transactionId, 0)));
        assertEquals(
                AccountEffectState.HELD,
                transactions.get(TransactionReference.id(transactionId))
                        .orElseThrow()
                        .getEffects()
                        .getFirst()
                        .getState());
    }

    @Test
    void duplicatePartialSettlementIsIdempotent() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLED),
                effect(2, AccountEffectState.SETTLING)));
        AccountHoldSettledHandler handler = new AccountHoldSettledHandler(transactions);

        handler.handle(new AccountHoldSettled(1, transactionId, 0));

        assertEquals(
                TransactionState.SETTLING,
                transactions.get(TransactionReference.id(transactionId))
                        .orElseThrow()
                        .getState());
    }

    private static Transaction transaction(
            TransactionState state,
            AccountEffect... effects) {
        return new Transaction(
                null, null, state, "request", "TRANSFER", "document", "description",
                Instant.EPOCH, Instant.EPOCH, null, null, List.of(), List.of(effects));
    }

    private static AccountEffect effect(long accountId, AccountEffectState state) {
        return new AccountEffect(
                accountId,
                new AccountNumber("ACCOUNT0" + accountId),
                DebitCredit.DEBIT,
                AMOUNT,
                state);
    }
}
