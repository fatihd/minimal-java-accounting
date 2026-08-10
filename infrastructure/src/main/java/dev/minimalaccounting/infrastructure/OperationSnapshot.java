package dev.minimalaccounting.infrastructure;

import lombok.NonNull;

import java.util.List;

public record OperationSnapshot(
        long attempts,
        long successes,
        long failures,
        long items,
        long totalNanoseconds,
        @NonNull List<Long> histogramUpperBoundsNanoseconds,
        @NonNull List<Long> histogramCounts) {
    public OperationSnapshot {
        histogramUpperBoundsNanoseconds = List.copyOf(histogramUpperBoundsNanoseconds);
        histogramCounts = List.copyOf(histogramCounts);
    }
}
