package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionFactory;
import dev.minimalaccounting.model.transaction.TransactionProcessManager;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class TransactionIntake implements Handler<SubmitTransaction> {
    @NonNull
    private final Repository<AccountIdentifier, Account> accounts;
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;
    @NonNull
    private final Repository<JournalIdentifier, JournalEntry> journals;
    @NonNull
    private final Sender<PlaceAccountHold> holds;
    @NonNull
    private final Sender<JournalizeTransaction> journalizations;
    @NonNull
    private final Clock clock;

    @Override
    public synchronized void handle(@NonNull SubmitTransaction submitted) {
        if (transactions.get(TransactionIdentifier.request(submitted.requestId())).isPresent()
                || journals.get(JournalIdentifier.request(submitted.requestId())).isPresent()) {
            return;
        }

        List<TransactionFactory.Line> lines = new ArrayList<>();
        for (var line : submitted.lines()) {
            Account account = accounts.get(AccountIdentifier.number(line.accountNumber()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Account does not exist: " + line.accountNumber().value()));
            lines.add(new TransactionFactory.Line(
                    account,
                    line.side(),
                    line.transactionAmount(),
                    line.ledgerAmount()));
        }
        Transaction transaction = TransactionFactory.create(
                        submitted.requestId(),
                        submitted.documentType(),
                        submitted.documentNumber(),
                        submitted.description(),
                        submitted.occurredAt(),
                        lines);

        transaction.start(clock.instant());
        transactions.add(transaction);
        var process = new TransactionProcessManager(transaction);
        for (PlaceAccountHold command : process.accountHoldsToPlace()) {
            holds.send(command);
        }
        process.transactionToJournalize().ifPresent(journalizations::send);
    }
}
