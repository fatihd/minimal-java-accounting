package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.AccountNumber;

public record ReleaseAccountHold(
        AccountNumber accountNumber,
        long transactionId,
        DebitCredit side,
        Money amount) {
}
