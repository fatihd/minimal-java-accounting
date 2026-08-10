package dev.minimalaccounting.model.account;

public record AccountHoldReleased(
        long accountId,
        long transactionId,
        int effectNumber) {
}
