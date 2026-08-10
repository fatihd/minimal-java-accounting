package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;
import lombok.NonNull;

public record AccountIdentifier(Long id, AccountNumber accountNumber) {
    public AccountIdentifier {
        if ((id == null) == (accountNumber == null)) {
            throw new IllegalArgumentException("Exactly one account identifier is required");
        }
    }

    public static AccountIdentifier id(long id) {
        return new AccountIdentifier(id, null);
    }

    public static AccountIdentifier number(@NonNull AccountNumber number) {
        return new AccountIdentifier(null, number);
    }
}
