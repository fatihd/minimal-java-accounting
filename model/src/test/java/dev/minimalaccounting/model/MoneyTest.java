package dev.minimalaccounting.model;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void performsCurrencySafeArithmetic() {
        Money amount = new Money(100, USD);

        assertEquals(new Money(125, USD), amount.plus(new Money(25, USD)));
        assertEquals(new Money(75, USD), amount.minus(new Money(25, USD)));
        assertEquals(new Money(110, USD), amount.plusMinor(10));
    }

    @Test
    void rejectsArithmeticAcrossCurrencies() {
        Money dollars = new Money(100, USD);
        Money euros = new Money(100, EUR);

        assertThrows(IllegalArgumentException.class, () -> dollars.plus(euros));
        assertThrows(IllegalArgumentException.class, () -> dollars.compareTo(euros));
    }

    @Test
    void appliesDebitAndCreditPolarity() {
        Money balance = new Money(100, USD);
        Money amount = new Money(25, USD);

        assertEquals(new Money(125, USD), DebitCredit.DEBIT.applyTo(balance, amount));
        assertEquals(new Money(75, USD), DebitCredit.CREDIT.applyTo(balance, amount));
        assertEquals(25, DebitCredit.DEBIT.signed(25));
        assertEquals(-25, DebitCredit.CREDIT.signed(25));
    }

    @Test
    void createsCurrencySpecificZero() {
        assertEquals(new Money(0, USD), Money.zero(USD));
    }
}
