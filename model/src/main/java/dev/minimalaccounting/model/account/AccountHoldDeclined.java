package dev.minimalaccounting.model.account;

public record AccountHoldDeclined(
        long accountId,
        long transactionId,
        int effectNumber,
        String reason) {
}
