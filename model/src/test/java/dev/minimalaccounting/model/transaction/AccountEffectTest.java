package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountEffectTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void heldEffectCanBeSettled() {
        AccountEffect effect = effect(AccountEffectState.PENDING);

        effect.hold();
        effect.beginSettlement();
        effect.settle();

        assertEquals(AccountEffectState.SETTLED, effect.getState());
    }

    @Test
    void heldEffectCanBeginReleaseIdempotently() {
        AccountEffect held = effect(AccountEffectState.HELD);

        held.beginRelease();
        held.beginRelease();

        assertEquals(AccountEffectState.RELEASING, held.getState());
    }

    @Test
    void releasingEffectCanBeReleased() {
        AccountEffect effect = effect(AccountEffectState.RELEASING);

        effect.release();
        effect.release();

        assertEquals(AccountEffectState.RELEASED, effect.getState());
    }

    @Test
    void settlingEffectCanBeSettledIdempotently() {
        AccountEffect effect = effect(AccountEffectState.SETTLING);

        effect.beginSettlement();
        effect.settle();
        effect.settle();

        assertEquals(AccountEffectState.SETTLED, effect.getState());
    }

    @Test
    void pendingEffectCanBeDeclined() {
        AccountEffect effect = effect(AccountEffectState.PENDING);

        assertTrue(effect.decline());
        assertFalse(effect.decline());

        assertEquals(AccountEffectState.DECLINED, effect.getState());
    }

    @Test
    void duplicateAndLateHoldResultsDoNotRegressTheEffect() {
        AccountEffect held = effect(AccountEffectState.HELD);

        assertTrue(held.hold());
        assertEquals(AccountEffectState.HELD, held.getState());
        for (AccountEffectState state : new AccountEffectState[] {
                AccountEffectState.RELEASING,
                AccountEffectState.RELEASED,
                AccountEffectState.SETTLING,
                AccountEffectState.SETTLED}) {
            AccountEffect effect = effect(state);
            assertFalse(effect.hold());
            assertEquals(state, effect.getState());
        }
    }

    @Test
    void conflictingAndPrematureTransitionsAreRejected() {
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.DECLINED).hold());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.HELD).decline());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.PENDING).beginRelease());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.DECLINED).beginRelease());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.HELD).release());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.PENDING).beginSettlement());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.HELD).settle());
    }

    @Test
    void onlyHeldEffectsBeginReleaseWhenRequestedConditionally() {
        AccountEffect held = effect(AccountEffectState.HELD);
        AccountEffect pending = effect(AccountEffectState.PENDING);

        held.beginReleaseIfHeld();
        pending.beginReleaseIfHeld();

        assertEquals(AccountEffectState.RELEASING, held.getState());
        assertEquals(AccountEffectState.PENDING, pending.getState());
    }

    @Test
    void netChangeCreatesAPendingEffectWithDirectionAndPositiveAmount() {
        AccountNumber accountNumber = new AccountNumber("ACCOUNT01");

        AccountEffect debit = AccountEffect.pending(
                1, accountNumber, new Money(100, Currency.getInstance("USD")));
        AccountEffect credit = AccountEffect.pending(
                1, accountNumber, new Money(-100, Currency.getInstance("USD")));

        assertAll(
                () -> assertEquals(DebitCredit.DEBIT, debit.getSide()),
                () -> assertEquals(AMOUNT, debit.getLedgerAmount()),
                () -> assertTrue(debit.isAwaitingHold()),
                () -> assertEquals(DebitCredit.CREDIT, credit.getSide()),
                () -> assertEquals(AMOUNT, credit.getLedgerAmount()),
                () -> assertTrue(credit.isAwaitingHold()));
    }

    @Test
    void effectRequiresARealAccountAndPositiveAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountEffect(
                        0,
                        new AccountNumber("ACCOUNT01"),
                        DebitCredit.DEBIT,
                        AMOUNT,
                        AccountEffectState.PENDING));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountEffect(
                        1,
                        new AccountNumber("ACCOUNT01"),
                        DebitCredit.DEBIT,
                        Money.zero(Currency.getInstance("USD")),
                        AccountEffectState.PENDING));
        assertThrows(
                IllegalArgumentException.class,
                () -> AccountEffect.pending(
                        1,
                        new AccountNumber("ACCOUNT01"),
                        Money.zero(Currency.getInstance("USD"))));
    }

    @Test
    void effectValidatesItsOwnAccountIdentity() {
        AccountEffect effect = effect(AccountEffectState.PENDING);

        effect.requireAccount(1);

        assertThrows(IllegalArgumentException.class, () -> effect.requireAccount(2));
    }

    @Test
    void stateQueriesDescribeOutstandingAndFinishedWork() {
        assertTrue(effect(AccountEffectState.HELD).isHeld());
        assertTrue(effect(AccountEffectState.RELEASING).isAwaitingRelease());
        assertTrue(effect(AccountEffectState.SETTLING).isAwaitingSettlement());
        assertTrue(effect(AccountEffectState.SETTLED).isSettled());
        assertTrue(effect(AccountEffectState.DECLINED).isReleaseFinished());
        assertTrue(effect(AccountEffectState.RELEASED).isReleaseFinished());
        assertFalse(effect(AccountEffectState.PENDING).isReleaseFinished());
    }

    private static AccountEffect effect(AccountEffectState state) {
        return new AccountEffect(
                1,
                new AccountNumber("ACCOUNT01"),
                DebitCredit.DEBIT,
                AMOUNT,
                state);
    }
}
