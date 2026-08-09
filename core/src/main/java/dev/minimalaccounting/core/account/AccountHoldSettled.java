package dev.minimalaccounting.core.account;

public record AccountHoldSettled(
        long accountId,
        long transactionId,
        int effectNumber) {
}
