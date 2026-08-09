package dev.minimalaccounting.core.account;

public record AccountHoldPlaced(
        long accountId,
        long transactionId,
        int effectNumber) {
}
