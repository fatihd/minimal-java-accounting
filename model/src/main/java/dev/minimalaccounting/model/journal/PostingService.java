package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.AccountNumber;
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
        Map<AccountNumber, Account> byNumber = new HashMap<>();
        for (Account account : accounts) {
            byNumber.put(account.getAccountNumber(), account);
        }

        Map<AccountNumber, Long> changes = new LinkedHashMap<>();
        for (TransactionLine line : transaction.getLines()) {
            Account account = account(byNumber, line);
            account.requireCurrency(line.getLedgerAmount());
            changes.merge(
                    account.getAccountNumber(), signed(line.getSide(), line.getLedgerAmount()),
                    Math::addExact);
        }
        for (Map.Entry<AccountNumber, Long> change : changes.entrySet()) {
            require(byNumber.get(change.getKey()))
                    .projectedBalance(change.getValue());
        }

        Map<AccountNumber, Long> running = new HashMap<>();
        for (Account account : accounts) {
            running.put(account.getAccountNumber(), account.getBalance().minor());
        }
        List<JournalLine> lines = new ArrayList<>();
        for (TransactionLine line : transaction.getLines()) {
            Account account = account(byNumber, line);
            long after = Math.addExact(
                    require(running.get(account.getAccountNumber())),
                    signed(line.getSide(), line.getLedgerAmount()));
            running.put(account.getAccountNumber(), after);
            lines.add(new JournalLine(
                    account.getAccountNumber(),
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
            Map<AccountNumber, Account> accounts,
            TransactionLine line) {
        Account account = accounts.get(line.getAccountNumber());
        if (account == null) {
            throw new PostingRejectedException("Account does not exist: "
                    + line.getAccountNumber().value());
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
