package dev.minimalaccounting.model.journal;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public class JournalEntry {
    @Setter
    private Long id;
    @Setter
    private Long revision;
    private String requestId;
    private String documentType;
    private String documentNumber;
    private String description;
    private Instant occurredAt;
    private Instant journalizedAt;
    @Getter(AccessLevel.NONE)
    private List<JournalLine> lines = new ArrayList<>();

    public static JournalEntry create(
            String requestId,
            String documentType,
            String documentNumber,
            String description,
            @NonNull Instant occurredAt,
            @NonNull Instant journalizedAt,
            @NonNull List<JournalLine> lines) {
        return new JournalEntry(
                null,
                null,
                requestId,
                documentType,
                documentNumber,
                description,
                occurredAt,
                journalizedAt,
                lines);
    }

    public JournalEntry(
            Long id,
            Long revision,
            String requestId,
            String documentType,
            String documentNumber,
            String description,
            Instant occurredAt,
            Instant journalizedAt,
            List<JournalLine> lines) {
        this.id = id;
        this.revision = revision;
        this.requestId = requireText(requestId, "requestId");
        this.documentType = requireText(documentType, "documentType");
        this.documentNumber = requireText(documentNumber, "documentNumber");
        this.description = description;
        this.occurredAt = occurredAt;
        this.journalizedAt = journalizedAt;
        List<JournalLine> values = lines;
        if (values.size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least two lines");
        }
        for (JournalLine line : values) {
            Objects.requireNonNull(line, "line");
            line.validate();
        }
        this.lines = new ArrayList<>(values);
    }

    public List<JournalLine> getLines() {
        return List.copyOf(lines);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
