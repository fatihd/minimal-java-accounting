package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.AccountNumber;

public record SettleAccountHold(
        AccountNumber accountNumber,
        long transactionId,
        int effectNumber,
        DebitCredit side,
        Money amount) {
}
