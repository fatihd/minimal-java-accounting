package dev.minimalaccounting.model.transaction;

import lombok.NonNull;

public record TransactionIdentifier(Long id, String requestId) {
    public TransactionIdentifier {
        if ((id == null) == (requestId == null)) {
            throw new IllegalArgumentException("Exactly one transaction identifier is required");
        }
        if (requestId != null && requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static TransactionIdentifier id(long id) {
        return new TransactionIdentifier(id, null);
    }

    public static TransactionIdentifier request(@NonNull String requestId) {
        return new TransactionIdentifier(null, requestId);
    }
}
