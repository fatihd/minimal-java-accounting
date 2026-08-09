package dev.minimalaccounting.core.account;

public record AccountHoldReleased(
        long accountId,
        long transactionId,
        int effectNumber) {
}
