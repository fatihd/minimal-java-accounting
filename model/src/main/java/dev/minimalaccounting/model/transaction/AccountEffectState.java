package dev.minimalaccounting.model.transaction;

public enum AccountEffectState {
    PENDING,
    HELD,
    DECLINED,
    RELEASING,
    RELEASED,
    SETTLING,
    SETTLED
}
