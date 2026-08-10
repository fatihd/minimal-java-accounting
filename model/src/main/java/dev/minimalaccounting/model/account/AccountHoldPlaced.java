package dev.minimalaccounting.model.account;

public record AccountHoldPlaced(
        long accountId,
        long transactionId,
        int effectNumber) {
}
