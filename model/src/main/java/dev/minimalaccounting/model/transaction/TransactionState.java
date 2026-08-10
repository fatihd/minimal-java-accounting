package dev.minimalaccounting.model.transaction;

public enum TransactionState {
    PLACING_HOLDS,
    HELD,
    RELEASING_HOLDS,
    SETTLING,
    COMPLETED,
    REJECTED
}
