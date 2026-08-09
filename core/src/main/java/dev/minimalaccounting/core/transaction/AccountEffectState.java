package dev.minimalaccounting.core.transaction;

public enum AccountEffectState {
    PENDING,
    HELD,
    DECLINED,
    RELEASING,
    RELEASED,
    SETTLING,
    SETTLED
}
