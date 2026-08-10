package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);
    @Test
    void allPlacedHoldsMoveTransactionToHeld() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING),
                effect(2, AccountEffectState.PENDING));
        transaction.placeEffect(account(1), Instant.EPOCH);

        transaction.placeEffect(account(2), Instant.EPOCH);

        assertEquals(TransactionState.HELD, transaction.getState());
    }

    @Test
    void holdPlacedAfterDeclineIsReleased() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING),
                effect(2, AccountEffectState.PENDING));
        transaction.declineEffect(account(1), "limit exceeded");

        transaction.placeEffect(account(2), Instant.EPOCH);

        assertEquals(
                AccountEffectState.RELEASING,
                transaction.getEffects().get(account(2)).getState());
    }

    @Test
    void duplicatePlacedHoldDuringReleaseMovesToReleasing() {
        Transaction transaction = transaction(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.HELD));

        transaction.placeEffect(account(2), Instant.EPOCH);

        assertEquals(
                AccountEffectState.RELEASING,
                transaction.getEffects().get(account(2)).getState());
    }

    @Test
    void duplicatePlacedHoldDuringPlacementIsIdempotent() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.HELD),
                effect(2, AccountEffectState.PENDING));

        transaction.placeEffect(account(1), Instant.EPOCH);

        assertEquals(TransactionState.PLACING_HOLDS, transaction.getState());
        assertEquals(
                AccountEffectState.HELD,
                transaction.getEffects().get(account(1)).getState());
    }

    @Test
    void latePlacedHoldDoesNotRegressSettlementOrCompletion() {
        for (TransactionState state : List.of(
                TransactionState.SETTLING,
                TransactionState.COMPLETED)) {
            Transaction transaction = transaction(
                    state,
                    effect(1, AccountEffectState.HELD));

            transaction.placeEffect(account(1), Instant.EPOCH);

            assertEquals(state, transaction.getState());
            assertEquals(
                    AccountEffectState.HELD,
                    transaction.getEffects().get(account(1)).getState());
        }
    }

    @Test
    void placedHoldInAnIncompatibleTransactionStateFailsForRetry() {
        Transaction transaction = transaction(
                TransactionState.REJECTED,
                effect(1, AccountEffectState.HELD));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.placeEffect(account(1), Instant.EPOCH));
    }

    @Test
    void pendingPlacementOutsidePlacementOrReleaseFailsForRetry() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.PENDING));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.placeEffect(account(1), Instant.EPOCH));
    }

    @Test
    void finalReleaseRejectsTransaction() {
        Transaction transaction = transaction(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.RELEASING));

        transaction.releaseEffect(account(2));

        assertEquals(TransactionState.REJECTED, transaction.getState());
    }

    @Test
    void duplicateReleaseKeepsTheTransactionRejected() {
        Transaction transaction = transaction(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.RELEASING));
        transaction.releaseEffect(account(2));

        transaction.releaseEffect(account(2));

        assertEquals(TransactionState.REJECTED, transaction.getState());
        assertEquals(AccountEffectState.RELEASED,
                transaction.getEffects().get(account(2)).getState());
    }

    @Test
    void releaseBeforeTheEffectIsReleasingFailsForRetry() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.HELD));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.releaseEffect(account(1)));
    }

    @Test
    void beginSettlementMovesTransactionToSettlement() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));

        transaction.beginSettlement("request");

        assertEquals(TransactionState.SETTLING, transaction.getState());
    }

    @Test
    void finalSettledHoldCompletesTransaction() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));
        transaction.beginSettlement("request");

        transaction.settleEffect(account(1));

        assertEquals(TransactionState.COMPLETED, transaction.getState());
    }

    @Test
    void duplicateSettlementKeepsTheTransactionCompleted() {
        Transaction transaction = transaction(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLING));
        transaction.settleEffect(account(1));

        transaction.settleEffect(account(1));

        assertEquals(TransactionState.COMPLETED, transaction.getState());
    }

    @Test
    void settlementBeforeTheEffectIsSettlingFailsForRetry() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.settleEffect(account(1)));
    }

    @Test
    void journalizedTransactionWithoutEffectsCompletesImmediately() {
        Transaction transaction = transaction(TransactionState.HELD);

        transaction.beginSettlement("request");

        assertEquals(TransactionState.COMPLETED, transaction.getState());
    }

    @Test
    void duplicateJournalizationDoesNotRestartSettlement() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));
        transaction.beginSettlement("request");

        transaction.beginSettlement("request");

        assertEquals(TransactionState.SETTLING, transaction.getState());
        assertEquals(AccountEffectState.SETTLING,
                transaction.getEffects().get(account(1)).getState());
    }

    @Test
    void journalizationBeforeAllHoldsArePlacedFailsForRetry() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.beginSettlement("request"));
    }

    @Test
    void duplicatePlacedEventAfterSettlementStartedIsStale() {
        Transaction transaction = transaction(
                TransactionState.SETTLING,
                effect(1, AccountEffectState.SETTLING));

        transaction.placeEffect(account(1), Instant.EPOCH.plusSeconds(1));

        assertEquals(TransactionState.SETTLING, transaction.getState());
        assertEquals(AccountEffectState.SETTLING,
                transaction.getEffects().get(account(1)).getState());
    }

    @Test
    void placedAndDeclinedResultsForTheSameEffectConflict() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING),
                effect(2, AccountEffectState.PENDING));
        transaction.placeEffect(account(1), Instant.EPOCH);

        assertThrows(
                IllegalStateException.class,
                () -> transaction.declineEffect(account(1), "declined too late"));
    }

    @Test
    void declinedAndPlacedResultsForTheSameEffectConflict() {
        Transaction transaction = transaction(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.placeEffect(account(1), Instant.EPOCH));
    }

    @Test
    void duplicateDeclineKeepsTheOriginalRejectionFlow() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING),
                effect(2, AccountEffectState.HELD));
        transaction.declineEffect(account(1), "limit exceeded");

        transaction.declineEffect(account(1), "different retry text");

        assertEquals(TransactionState.RELEASING_HOLDS, transaction.getState());
        assertEquals("limit exceeded", transaction.getFailureReason());
        assertEquals(AccountEffectState.RELEASING,
                transaction.getEffects().get(account(2)).getState());
    }

    @Test
    void lateDeclineOfAnotherPendingEffectCompletesRejection() {
        Transaction transaction = transaction(
                TransactionState.RELEASING_HOLDS,
                effect(1, AccountEffectState.DECLINED),
                effect(2, AccountEffectState.PENDING));

        transaction.declineEffect(account(2), "also declined");

        assertEquals(TransactionState.REJECTED, transaction.getState());
        assertEquals(AccountEffectState.DECLINED,
                transaction.getEffects().get(account(2)).getState());
    }

    @Test
    void pendingDeclineOutsidePlacementOrReleaseFailsForRetry() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.PENDING));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.declineEffect(account(1), "late decline"));
    }

    @Test
    void rejectsUnknownAccountEffect() {
        Transaction transaction = transaction(
                TransactionState.PLACING_HOLDS,
                effect(1, AccountEffectState.PENDING));

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.placeEffect(account(2), Instant.EPOCH));
    }

    @Test
    void rejectsJournalForAnotherTransaction() {
        Transaction transaction = transaction(
                TransactionState.HELD,
                effect(1, AccountEffectState.HELD));

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.beginSettlement("another-request"));
    }

    @Test
    void duplicateJournalizationAfterCompletionIsStale() {
        Transaction transaction = transaction(
                TransactionState.COMPLETED,
                effect(1, AccountEffectState.SETTLED));

        transaction.beginSettlement("request");

        assertEquals(TransactionState.COMPLETED, transaction.getState());
        assertEquals(
                AccountEffectState.SETTLED,
                transaction.getEffects().get(account(1)).getState());
    }

    @Test
    void rejectsUnbalancedLinesWhenStarted() {
        Transaction transaction = new Transaction(
                null,
                null,
                null,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                null,
                null,
                null,
                List.of(
                        new TransactionLine(
                                new AccountNumber("ACCOUNT01"), DebitCredit.DEBIT,
                                AMOUNT, AMOUNT),
                        new TransactionLine(
                                new AccountNumber("ACCOUNT02"), DebitCredit.CREDIT,
                                new Money(99, USD), new Money(99, USD))),
                Map.of());

        assertThrows(
                PostingRejectedException.class,
                () -> transaction.start(Instant.EPOCH));
    }

    private static Effect effect(long accountId, AccountEffectState state) {
        return new Effect(
                account(accountId),
                new AccountEffect(DebitCredit.DEBIT, AMOUNT, state));
    }

    private static AccountNumber account(long id) {
        return new AccountNumber("ACCOUNT0" + id);
    }

    private static Transaction transaction(
            TransactionState state,
            Effect... effects) {
        Map<AccountNumber, AccountEffect> effectMap = new TreeMap<>();
        for (Effect effect : effects) {
            effectMap.put(effect.accountNumber(), effect.value());
        }
        return new Transaction(
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
                effectMap);
    }

    private record Effect(AccountNumber accountNumber, AccountEffect value) {
    }
}
