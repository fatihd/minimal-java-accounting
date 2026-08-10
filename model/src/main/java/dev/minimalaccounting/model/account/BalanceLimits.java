package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import lombok.NonNull;

public record BalanceLimits(Money minimum, Money maximum) {
    public BalanceLimits {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "minimumBalance must not exceed maximumBalance");
        }
    }

    public void requireContains(@NonNull Money balance) {
        if (minimum != null && balance.compareTo(minimum) < 0) {
            throw new PostingRejectedException("Balance would fall below its minimum");
        }
        if (maximum != null && balance.compareTo(maximum) > 0) {
            throw new PostingRejectedException("Balance would exceed its maximum");
        }
    }
}
