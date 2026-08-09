package dev.minimalaccounting.core.transaction;

public enum TransactionState {
    PLACING_HOLDS,
    HELD,
    RELEASING_HOLDS,
    SETTLING,
    COMPLETED,
    REJECTED
}
