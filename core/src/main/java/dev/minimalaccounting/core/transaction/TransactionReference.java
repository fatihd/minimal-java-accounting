package dev.minimalaccounting.core.transaction;

import lombok.NonNull;

public record TransactionReference(Long id, String requestId) {
    public TransactionReference {
        if ((id == null) == (requestId == null)) {
            throw new IllegalArgumentException("Exactly one transaction reference is required");
        }
        if (requestId != null && requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static TransactionReference id(long id) {
        return new TransactionReference(id, null);
    }

    public static TransactionReference request(@NonNull String requestId) {
        return new TransactionReference(null, requestId);
    }
}
