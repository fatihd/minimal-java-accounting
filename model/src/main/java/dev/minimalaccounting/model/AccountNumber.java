package dev.minimalaccounting.model;

import lombok.NonNull;

public record AccountNumber(@NonNull String value) implements Comparable<AccountNumber> {
    public AccountNumber {
        if (!value.matches("[A-Z0-9]{8,24}")) {
            throw new IllegalArgumentException(
                    "Account number must be 8-24 uppercase letters or digits");
        }
    }

    @Override
    public int compareTo(@NonNull AccountNumber other) {
        return value.compareTo(other.value);
    }
}
