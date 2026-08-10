package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.transaction.Transaction;
import dev.minimalaccounting.core.transaction.TransactionFactory;
import dev.minimalaccounting.core.transaction.TransactionProcessManager;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.ports.persistence.JournalRepository;
import dev.minimalaccounting.ports.messaging.Sender;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class TransactionIntake implements SubmitTransactionHandler {
    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final TransactionRepository transactions;
    @NonNull
    private final JournalRepository journals;
    @NonNull
    private final Sender<PlaceAccountHold> holds;
    @NonNull
    private final Sender<JournalizeTransaction> journalizations;
    @NonNull
    private final Clock clock;

    @Override
    public synchronized void handle(
            @NonNull dev.minimalaccounting.ports.api.SubmitTransaction submitted) {
        if (transactions.get(TransactionReference.request(submitted.requestId())).isPresent()
                || journals.get(JournalReference.request(submitted.requestId())).isPresent()) {
            return;
        }

        List<TransactionFactory.Line> lines = new ArrayList<>();
        for (var line : submitted.lines()) {
            Account account = accounts.get(AccountReference.number(line.accountNumber()))
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
