package dev.minimalaccounting.core.account;

import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;

public record PlaceAccountHold(
        long accountId,
        long transactionId,
        int effectNumber,
        DebitCredit side,
        Money amount) {
}
