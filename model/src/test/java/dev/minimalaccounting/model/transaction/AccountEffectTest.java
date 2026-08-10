package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void pendingOrHeldEffectCanBeginRelease() {
        AccountEffect pending = effect(AccountEffectState.PENDING);
        AccountEffect held = effect(AccountEffectState.HELD);

        pending.beginRelease();
        pending.beginRelease();
        held.beginRelease();

        assertEquals(AccountEffectState.RELEASING, pending.getState());
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

        effect.decline();

        assertEquals(AccountEffectState.DECLINED, effect.getState());
    }

    @Test
    void invalidTransitionsAreRejected() {
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.HELD).hold());
        assertThrows(
                IllegalStateException.class,
                () -> effect(AccountEffectState.HELD).decline());
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

    private static AccountEffect effect(AccountEffectState state) {
        return new AccountEffect(
                1,
                new AccountNumber("ACCOUNT01"),
                DebitCredit.DEBIT,
                AMOUNT,
                state);
    }
}
