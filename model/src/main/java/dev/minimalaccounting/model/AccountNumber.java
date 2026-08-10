package dev.minimalaccounting.model;

import lombok.NonNull;

public record AccountNumber(@NonNull String value) {
    public AccountNumber {
        if (!value.matches("[A-Z0-9]{8,24}")) {
            throw new IllegalArgumentException(
                    "Account number must be 8-24 uppercase letters or digits");
        }
    }
}
