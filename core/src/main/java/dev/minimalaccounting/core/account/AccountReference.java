package dev.minimalaccounting.core.account;

import dev.minimalaccounting.shared.AccountNumber;
import lombok.NonNull;

public record AccountReference(Long id, AccountNumber accountNumber) {
    public AccountReference {
        if ((id == null) == (accountNumber == null)) {
            throw new IllegalArgumentException("Exactly one account reference is required");
        }
    }

    public static AccountReference id(long id) {
        return new AccountReference(id, null);
    }

    public static AccountReference number(@NonNull AccountNumber number) {
        return new AccountReference(null, number);
    }
}
