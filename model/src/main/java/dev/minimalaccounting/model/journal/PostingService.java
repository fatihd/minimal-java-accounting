package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionLine;
import lombok.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PostingService {
    public JournalEntry post(
            Transaction transaction,
            Collection<Account> accounts,
            @NonNull Instant journalizedAt) {
        Map<Long, Account> byId = new HashMap<>();
        for (Account account : accounts) {
            byId.put(account.getId(), account);
        }

        Map<Long, Long> changes = new LinkedHashMap<>();
        for (TransactionLine line : transaction.getLines()) {
            Account account = account(byId, line);
            account.requireCurrency(line.getLedgerAmount());
            changes.merge(
                    account.getId(), signed(line.getSide(), line.getLedgerAmount()),
                    Math::addExact);
        }
        for (Map.Entry<Long, Long> change : changes.entrySet()) {
            require(byId.get(change.getKey()))
                    .projectedBalance(change.getValue());
        }

        Map<Long, Long> running = new HashMap<>();
        for (Account account : accounts) {
            running.put(account.getId(), account.getBalance().minor());
        }
        List<JournalLine> lines = new ArrayList<>();
        for (TransactionLine line : transaction.getLines()) {
            Account account = account(byId, line);
            long after = Math.addExact(
                    require(running.get(account.getId())),
                    signed(line.getSide(), line.getLedgerAmount()));
            running.put(account.getId(), after);
            lines.add(new JournalLine(
                    account.getId(),
                    line.getSide(),
                    line.getTransactionAmount(),
                    line.getLedgerAmount(),
                    new Money(after, account.getBalance().currency())));
        }

        return JournalEntry.create(
                transaction.getRequestId(),
                transaction.getDocumentType(),
                transaction.getDocumentNumber(),
                transaction.getDescription(),
                transaction.getOccurredAt(),
                journalizedAt,
                lines);
    }

    private static Account account(
            Map<Long, Account> accounts,
            TransactionLine line) {
        Account account = accounts.get(line.getAccountId());
        if (account == null) {
            throw new PostingRejectedException("Account does not exist: "
                    + line.getAccountNumber().value());
        }
        if (!account.getAccountNumber().equals(line.getAccountNumber())) {
            throw new PostingRejectedException(
                    "Account identity does not match transaction line");
        }
        return account;
    }

    private static long signed(DebitCredit side, Money amount) {
        return side.signed(amount.minor());
    }

    private static <T> T require(@NonNull T value) {
        return value;
    }
}
