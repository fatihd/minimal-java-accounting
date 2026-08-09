package dev.minimalaccounting.core.account;

public record AccountHoldDeclined(
        long accountId,
        long transactionId,
        int effectNumber,
        String reason) {
}
