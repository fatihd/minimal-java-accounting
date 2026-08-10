package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;

public record AccountHoldSettled(
        AccountNumber accountNumber,
        long transactionId,
        int effectNumber) {
}
