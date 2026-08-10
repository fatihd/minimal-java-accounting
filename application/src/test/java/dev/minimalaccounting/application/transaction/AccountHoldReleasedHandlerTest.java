package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.transaction.AccountEffect;
import dev.minimalaccounting.model.transaction.AccountEffectState;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;

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
                () -> handler.handle(new AccountHoldReleased(
                        new AccountNumber("ACCOUNT01"), 99)));
    }

    @Test
    void prematureReleaseCompletionNacksWithoutChangingState() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction(
                TransactionState.PLACING_HOLDS,
                Map.of(account(), effect(AccountEffectState.HELD)))).id();
        AccountHoldReleasedHandler handler = new AccountHoldReleasedHandler(transactions);

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new AccountHoldReleased(
                        new AccountNumber("ACCOUNT01"), transactionId)));
        assertEquals(
                AccountEffectState.HELD,
                transactions.get(TransactionIdentifier.id(transactionId))
                        .orElseThrow()
                        .getEffects()
                        .get(account())
                        .getState());
    }

    @Test
    void duplicateReleaseCompletionIsIdempotent() {
        ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction(
                TransactionState.RELEASING_HOLDS,
                Map.of(account(), effect(AccountEffectState.RELEASING)))).id();
        AccountHoldReleasedHandler handler = new AccountHoldReleasedHandler(transactions);
        AccountHoldReleased event = new AccountHoldReleased(
                new AccountNumber("ACCOUNT01"), transactionId);

        handler.handle(event);
        handler.handle(event);

        assertEquals(
                TransactionState.REJECTED,
                transactions.get(TransactionIdentifier.id(transactionId))
                        .orElseThrow()
                        .getState());
    }

    private static Transaction transaction(
            TransactionState state,
            Map<AccountNumber, AccountEffect> effects) {
        return new Transaction(
                null, null, state, "request", "TRANSFER", "document", "description",
                Instant.EPOCH, Instant.EPOCH, null, null, List.of(), effects);
    }

    private static AccountEffect effect(AccountEffectState state) {
        return new AccountEffect(
                DebitCredit.DEBIT, AMOUNT, state);
    }

    private static AccountNumber account() {
        return new AccountNumber("ACCOUNT01");
    }
}
