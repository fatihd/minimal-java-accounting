package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;

public record AccountHoldDeclined(
        AccountNumber accountNumber,
        long transactionId,
        int effectNumber,
        String reason) {
}
