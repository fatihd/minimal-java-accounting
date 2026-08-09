package dev.minimalaccounting.shared;

public enum DebitCredit {
    DEBIT,
    CREDIT;

    public long signed(long amount) {
        return this == DEBIT ? amount : Math.negateExact(amount);
    }

    public Money applyTo(Money balance, Money amount) {
        return this == DEBIT ? balance.plus(amount) : balance.minus(amount);
    }
}
