package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountHoldTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void requiresAValidBusinessIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(0, 0, AMOUNT, AccountHoldState.HELD, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(10, -1, AMOUNT, AccountHoldState.HELD, null));
    }

    @Test
    void requiresAPositiveAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(
                        10, 0, Money.zero(AMOUNT.currency()),
                        AccountHoldState.HELD, null));
    }

    @Test
    void permitsAReasonOnlyForADeclinedHold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(10, 0, AMOUNT, AccountHoldState.DECLINED, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> hold(10, 0, AMOUNT, AccountHoldState.HELD, "not allowed"));
    }

    private static AccountHold hold(
            long transactionId,
            int effectNumber,
            Money amount,
            AccountHoldState state,
            String reason) {
        return new AccountHold(
                transactionId,
                effectNumber,
                DebitCredit.DEBIT,
                amount,
                state,
                reason);
    }
}
