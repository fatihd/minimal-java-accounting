package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import lombok.NonNull;

import java.time.Instant;
import java.util.List;

public record SubmitTransaction(
        @NonNull String requestId,
        @NonNull String documentType,
        @NonNull String documentNumber,
        String description,
        @NonNull Instant occurredAt,
        @NonNull List<Line> lines) {
    public SubmitTransaction {
        lines = List.copyOf(lines);
    }

    public record Line(
            @NonNull AccountNumber accountNumber,
            @NonNull DebitCredit side,
            @NonNull Money transactionAmount,
            @NonNull Money ledgerAmount) {
    }
}
