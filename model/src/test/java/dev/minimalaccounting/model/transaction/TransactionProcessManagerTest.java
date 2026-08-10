package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionProcessManagerTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);

    @Test
    void pendingEffectsBecomeAccountHoldCommands() {
        var process = process(
                TransactionState.PLACING_HOLDS,
                effect(2, AccountEffectState.PENDING),
                effect(1, AccountEffectState.PENDING));

        assertEquals(
                List.of(
                        new PlaceAccountHold(
                                account(1), 10, DebitCredit.DEBIT, AMOUNT),
                        new PlaceAccountHold(
                                account(2), 10, DebitCredit.DEBIT, AMOUNT)),
                process.accountHoldsToPlace());
    }

    @Test
    void placementCommandsExcludeEffectsAlreadyHandled() {
        var process = process(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.HELD),
                effect(2, AccountEffectState.PENDING));

        assertEquals(
                List.of(new PlaceAccountHold(
                        account(2), 10, DebitCredit.DEBIT, AMOUNT)),
                process.accountHoldsToPlace());
    }

    @Test
    void placementBatchIsEmptyOutsideThePlacementFlow() {
        var process = process(TransactionState.HELD);

        assertTrue(process.accountHoldsToPlace().isEmpty());
    }

    @Test
    void heldTransactionBecomesJournalizationCommand() {
        var process = process(TransactionState.HELD);

        assertEquals(
                new JournalizeTransaction(10),
                process.transactionToJournalize().orElseThrow());
    }

    @Test
    void lastPlacedHoldMakesTransactionReadyForJournalization() {
        var process = process(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.HELD),
                effect(2, AccountEffectState.PENDING));

        process.accountHoldPlaced(
                new AccountHoldPlaced(account(2), 10), Instant.EPOCH);

        assertTrue(process.transactionToJournalize().isPresent());
    }

    @Test
    void duplicatePlacedEventReissuesJournalization() {
        var process = process(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));

        process.accountHoldPlaced(
                new AccountHoldPlaced(account(1), 10), Instant.EPOCH);

        assertEquals(
                new JournalizeTransaction(10),
                process.transactionToJournalize().orElseThrow());
    }

    @Test
    void latePlacedHoldBecomesReleaseCommand() {
        var process = process(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.PENDING));

        process.accountHoldPlaced(
                new AccountHoldPlaced(account(2), 10), Instant.EPOCH);

        assertEquals(
                new ReleaseAccountHold(
                        account(2), 10, DebitCredit.DEBIT, AMOUNT),
                process.accountHoldToRelease(account(2)).orElseThrow());
    }

    @Test
    void individualReleaseLookupIgnoresAnEffectAlreadyReleased() {
        var process = process(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.RELEASED));

        assertTrue(process.accountHoldToRelease(account(1)).isEmpty());
    }

    @Test
    void releaseBatchIsEmptyOutsideTheReleaseFlow() {
        var process = process(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING));

        assertTrue(process.accountHoldsToRelease().isEmpty());
    }

    @Test
    void individualReleaseLookupRejectsUnknownAccounts() {
        var process = process(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.RELEASING));

        assertThrows(
                IllegalArgumentException.class,
                () -> process.accountHoldToRelease(account(2)));
    }

    @Test
    void declinedHoldReleasesPreviouslyPlacedHolds() {
        var process = process(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING),
                effect(2, AccountEffectState.HELD));

        process.accountHoldDeclined(new AccountHoldDeclined(
                account(1), 10, "limit exceeded"));

        assertEquals(
                List.of(new ReleaseAccountHold(
                        account(2), 10, DebitCredit.DEBIT, AMOUNT)),
                process.accountHoldsToRelease());
    }

    @Test
    void duplicateDeclineReissuesOutstandingReleaseCommands() {
        var process = process(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.RELEASING));

        process.accountHoldDeclined(new AccountHoldDeclined(
                account(1), 10, "limit exceeded"));

        assertEquals(
                List.of(new ReleaseAccountHold(
                        account(2), 10, DebitCredit.DEBIT, AMOUNT)),
                process.accountHoldsToRelease());
    }

    @Test
    void journalizedTransactionBecomesSettlementCommands() {
        var process = process(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));

        process.transactionJournalized(
                new TransactionJournalized(10, 20), "request");

        assertEquals(
                List.of(new SettleAccountHold(
                        account(1), 10, DebitCredit.DEBIT, AMOUNT)),
                process.accountHoldsToSettle());
    }

    @Test
    void duplicateJournalizationReissuesOnlyOutstandingSettlementCommands() {
        var process = process(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLED),
                effect(2, AccountEffectState.SETTLING));

        process.transactionJournalized(
                new TransactionJournalized(10, 20), "request");

        assertEquals(
                List.of(new SettleAccountHold(
                        account(2), 10, DebitCredit.DEBIT, AMOUNT)),
                process.accountHoldsToSettle());
    }

    @Test
    void stalePlacedEventAfterSettlementDoesNotRegressToEarlierWork() {
        var process = process(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLING));

        process.accountHoldPlaced(
                new AccountHoldPlaced(account(1), 10), Instant.EPOCH);

        assertTrue(process.accountHoldToRelease(account(1)).isEmpty());
        assertTrue(process.transactionToJournalize().isEmpty());
        assertEquals(1, process.accountHoldsToSettle().size());
    }

    @Test
    void causallyPrematureCompletionEventsFailForRetry() {
        var release = process(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.HELD));
        var settlement = process(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));

        assertThrows(
                IllegalStateException.class,
                () -> release.accountHoldReleased(
                        new AccountHoldReleased(account(1), 10)));
        assertThrows(
                IllegalStateException.class,
                () -> settlement.accountHoldSettled(
                        new AccountHoldSettled(account(1), 10)));
    }

    @Test
    void releasedHoldClearsOutstandingReleaseCommands() {
        var process = process(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.RELEASING));

        process.accountHoldReleased(new AccountHoldReleased(account(2), 10));

        assertFalse(process.completed());
        assertTrue(process.accountHoldsToRelease().isEmpty());
    }

    @Test
    void journalizedTransactionWithoutEffectsCompletes() {
        var process = process(TransactionState.HELD);

        process.transactionJournalized(
                new TransactionJournalized(10, 20), "request");

        assertTrue(process.completed());
    }

    @Test
    void partialSettlementDoesNotCompleteTransaction() {
        var process = process(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLING),
                effect(2, AccountEffectState.SETTLING));

        process.accountHoldSettled(new AccountHoldSettled(account(1), 10));

        assertFalse(process.completed());
    }

    @Test
    void settlementBatchIsEmptyOutsideTheSettlementFlow() {
        var process = process(TransactionState.HELD);

        assertTrue(process.accountHoldsToSettle().isEmpty());
    }

    @Test
    void finalSettlementCompletesTransaction() {
        var process = process(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLED),
                effect(2, AccountEffectState.SETTLING));

        process.accountHoldSettled(new AccountHoldSettled(account(2), 10));

        assertTrue(process.completed());
    }

    @Test
    void rejectsEventForAnotherTransaction() {
        var process = process(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING));

        assertThrows(
                IllegalArgumentException.class,
                () -> process.accountHoldPlaced(
                        new AccountHoldPlaced(account(1), 11), Instant.EPOCH));
    }

    @Test
    void commandDerivationRequiresAPersistedTransactionIdentity() {
        Transaction transaction = new Transaction(
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
                List.of(),
                Map.of());
        var process = new TransactionProcessManager(transaction);

        assertThrows(
                IllegalStateException.class,
                process::transactionToJournalize);
    }

    private static TransactionProcessManager process(
            TransactionState state,
            Effect... effects) {
        Map<AccountNumber, AccountEffect> effectMap = new TreeMap<>();
        for (Effect effect : effects) {
            effectMap.put(effect.accountNumber(), effect.value());
        }
        return new TransactionProcessManager(new Transaction(
                10L,
                0L,
                state,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                state == TransactionState.HELD ? Instant.EPOCH : null,
                null,
                List.of(),
                effectMap));
    }

    private static Effect effect(long accountId, AccountEffectState state) {
        return new Effect(
                account(accountId),
                new AccountEffect(DebitCredit.DEBIT, AMOUNT, state));
    }

    private static AccountNumber account(long id) {
        return new AccountNumber("ACCOUNT0" + id);
    }

    private record Effect(AccountNumber accountNumber, AccountEffect value) {
    }
}
