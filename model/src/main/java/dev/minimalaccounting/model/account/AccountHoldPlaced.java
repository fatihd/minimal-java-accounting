package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;

public record AccountHoldPlaced(
        AccountNumber accountNumber,
        long transactionId,
        int effectNumber) {
}
