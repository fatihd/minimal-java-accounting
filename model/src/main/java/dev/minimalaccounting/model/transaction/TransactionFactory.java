package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import dev.minimalaccounting.model.account.Account;
import lombok.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TransactionFactory {
    private TransactionFactory() {
    }

    public static Transaction create(
            String requestId,
            String documentType,
            String documentNumber,
            String description,
            Instant occurredAt,
            @NonNull List<Line> lines) {
        List<Line> values = List.copyOf(lines);
        List<TransactionLine> transactionLines = new ArrayList<>(values.size());
        for (Line line : values) {
            Account account = line.account();
            Long accountId = account.getId();
            if (accountId == null || accountId < 1) {
                throw new PostingRejectedException("accountId must be positive");
            }
            account.requireCurrency(line.ledgerAmount());
            transactionLines.add(new TransactionLine(
                    accountId,
                    account.getAccountNumber(),
                    line.side(),
                    line.transactionAmount(),
                    line.ledgerAmount()));
        }
        return new Transaction(
                null,
                null,
                null,
                requestId,
                documentType,
                documentNumber,
                description,
                occurredAt,
                null,
                null,
                null,
                transactionLines,
                List.of());
    }

    public record Line(
            @NonNull Account account,
            @NonNull DebitCredit side,
            @NonNull Money transactionAmount,
            @NonNull Money ledgerAmount) {
    }
}
