package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;

public record AccountHoldReleased(
        AccountNumber accountNumber,
        long transactionId,
        int effectNumber) {
}
