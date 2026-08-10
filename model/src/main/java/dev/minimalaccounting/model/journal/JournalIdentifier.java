package dev.minimalaccounting.model.journal;

import lombok.NonNull;

public record JournalIdentifier(Long id, String requestId) {
    public JournalIdentifier {
        if ((id == null) == (requestId == null)) {
            throw new IllegalArgumentException("Exactly one journal identifier is required");
        }
        if (requestId != null && requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static JournalIdentifier id(long id) {
        return new JournalIdentifier(id, null);
    }

    public static JournalIdentifier request(@NonNull String requestId) {
        return new JournalIdentifier(null, requestId);
    }
}
