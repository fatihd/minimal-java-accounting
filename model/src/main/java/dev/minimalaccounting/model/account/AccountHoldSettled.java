package dev.minimalaccounting.model.account;

public record AccountHoldSettled(
        long accountId,
        long transactionId,
        int effectNumber) {
}
