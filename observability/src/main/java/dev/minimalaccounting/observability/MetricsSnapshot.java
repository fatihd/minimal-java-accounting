package dev.minimalaccounting.observability;

import lombok.NonNull;

import java.time.Instant;
import java.util.Map;

public record MetricsSnapshot(
        @NonNull Instant capturedAt,
        @NonNull Map<String, OperationSnapshot> operations) {
    public MetricsSnapshot {
        operations = Map.copyOf(operations);
    }
}
