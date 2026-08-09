package dev.minimalaccounting.core.account;

import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.shared.PostingRejectedException;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BalanceLimitsTest {
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void acceptsABalanceInsideTheRange() {
        BalanceLimits limits = new BalanceLimits(
                new Money(0, USD), new Money(1_000, USD));

        assertDoesNotThrow(() -> limits.requireContains(new Money(500, USD)));
    }

    @Test
    void rejectsABalanceOutsideEitherLimit() {
        BalanceLimits limits = new BalanceLimits(
                new Money(0, USD), new Money(1_000, USD));

        assertThrows(
                PostingRejectedException.class,
                () -> limits.requireContains(new Money(-1, USD)));
        assertThrows(
                PostingRejectedException.class,
                () -> limits.requireContains(new Money(1_001, USD)));
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BalanceLimits(new Money(1_000, USD), new Money(0, USD)));
    }

    @Test
    void rejectsLimitsInDifferentCurrencies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BalanceLimits(
                        new Money(0, USD),
                        new Money(1_000, Currency.getInstance("EUR"))));
    }
}
