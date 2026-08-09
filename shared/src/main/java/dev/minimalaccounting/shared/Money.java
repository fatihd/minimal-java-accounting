package dev.minimalaccounting.shared;

import lombok.NonNull;

import java.util.Currency;

public record Money(long minor, @NonNull Currency currency) implements Comparable<Money> {
    public static Money zero(@NonNull Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(@NonNull Money amount) {
        requireSameCurrency(amount);
        return new Money(Math.addExact(minor, amount.minor), currency);
    }

    public Money minus(@NonNull Money amount) {
        requireSameCurrency(amount);
        return new Money(Math.subtractExact(minor, amount.minor), currency);
    }

    public Money plusMinor(long change) {
        return new Money(Math.addExact(minor, change), currency);
    }

    public boolean hasSameCurrency(@NonNull Money amount) {
        return currency.equals(amount.currency);
    }

    @Override
    public int compareTo(@NonNull Money amount) {
        requireSameCurrency(amount);
        return Long.compare(minor, amount.minor);
    }

    private void requireSameCurrency(Money amount) {
        if (!hasSameCurrency(amount)) {
            throw new IllegalArgumentException(
                    "Cannot combine " + currency + " and " + amount.currency);
        }
    }
}
