package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountHoldTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void requiresAValidBusinessIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(0, AMOUNT, AccountHoldState.HELD, null));
    }

    @Test
    void requiresAPositiveAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(
                        10, Money.zero(AMOUNT.currency()),
                        AccountHoldState.HELD, null));
    }

    @Test
    void permitsAReasonOnlyForADeclinedHold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(10, AMOUNT, AccountHoldState.DECLINED, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(10, AMOUNT, AccountHoldState.HELD, "not allowed"));
    }

    @Test
    void placementConfirmationOwnsRetryAndConflictRules() {
        AccountHold held = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);
        AccountHold released = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);
        released.release();
        AccountHold declined = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);
        declined.decline("limit exceeded");

        assertEquals(
                AccountHoldState.HELD,
                held.confirmPlacement(DebitCredit.DEBIT, AMOUNT));
        assertEquals(
                AccountHoldState.RELEASED,
                released.confirmPlacement(DebitCredit.DEBIT, AMOUNT));
        assertThrows(
                IllegalArgumentException.class,
                () -> held.confirmPlacement(DebitCredit.CREDIT, AMOUNT));
        assertThrows(
                PostingRejectedException.class,
                () -> declined.confirmPlacement(DebitCredit.DEBIT, AMOUNT));
    }

    @Test
    void holdOwnsItsTransactionIdentity() {
        AccountHold hold = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);

        assertTrue(hold.hasIdentity(10));
        assertFalse(hold.hasIdentity(11));
    }

    @Test
    void heldAmountContributesOnlyWhileHeldAndOnTheMatchingSide() {
        AccountHold hold = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);

        assertEquals(AMOUNT, hold.heldAmount(DebitCredit.DEBIT));
        assertEquals(Money.zero(AMOUNT.currency()), hold.heldAmount(DebitCredit.CREDIT));

        hold.release();

        assertEquals(Money.zero(AMOUNT.currency()), hold.heldAmount(DebitCredit.DEBIT));
    }

    @Test
    void settlementRequirementHandlesRetriesAndConflicts() {
        AccountHold held = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);
        AccountHold settled = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);
        settled.settle();
        AccountHold released = AccountHold.placed(10, DebitCredit.DEBIT, AMOUNT);
        released.release();

        assertTrue(held.requiresSettlement());
        assertFalse(settled.requiresSettlement());
        assertThrows(IllegalStateException.class, released::requiresSettlement);
    }

    private static AccountHold hold(
            long transactionId,
            Money amount,
            AccountHoldState state,
            String reason) {
        return new AccountHold(
                transactionId,
                DebitCredit.DEBIT,
                amount,
                state,
                reason);
    }
}
