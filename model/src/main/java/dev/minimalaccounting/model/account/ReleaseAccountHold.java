package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;

public record ReleaseAccountHold(
        long accountId,
        long transactionId,
        int effectNumber,
        DebitCredit side,
        Money amount) {
}
