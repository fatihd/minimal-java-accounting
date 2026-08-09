package dev.minimalaccounting.core.journal;

import lombok.NonNull;

public record JournalReference(Long id, String requestId) {
    public JournalReference {
        if ((id == null) == (requestId == null)) {
            throw new IllegalArgumentException("Exactly one journal reference is required");
        }
        if (requestId != null && requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static JournalReference id(long id) {
        return new JournalReference(id, null);
    }

    public static JournalReference request(@NonNull String requestId) {
        return new JournalReference(null, requestId);
    }
}
