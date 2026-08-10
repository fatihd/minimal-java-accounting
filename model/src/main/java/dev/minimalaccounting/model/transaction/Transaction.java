package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public class Transaction {
    @Setter
    private Long id;
    @Setter
    private Long revision;
    private TransactionState state;
    private String requestId;
    private String documentType;
    private String documentNumber;
    private String description;
    private Instant occurredAt;
    private Instant receivedAt;
    private Instant heldAt;
    private String failureReason;
    @Getter(AccessLevel.NONE)
    private List<TransactionLine> lines = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private AccountEffects effects = new AccountEffects(Map.of());

    public Transaction(
            Long id,
            Long revision,
            TransactionState state,
            String requestId,
            String documentType,
            String documentNumber,
            String description,
            Instant occurredAt,
            Instant receivedAt,
            Instant heldAt,
            String failureReason,
            @NonNull List<TransactionLine> lines,
            @NonNull Map<AccountNumber, AccountEffect> effects) {
        this.id = id;
        this.revision = revision;
        this.state = state;
        this.requestId = requestId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.description = description;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.heldAt = heldAt;
        this.failureReason = failureReason;
        this.lines = new ArrayList<>(lines);
        this.effects = new AccountEffects(effects);
    }

    public List<TransactionLine> getLines() {
        return List.copyOf(lines);
    }

    public NavigableMap<AccountNumber, AccountEffect> getEffects() {
        return effects.snapshot();
    }

    public Set<AccountNumber> accountNumbers() {
        Set<AccountNumber> result = new LinkedHashSet<>();
        for (TransactionLine line : lines) {
            result.add(line.getAccountNumber());
        }
        return result;
    }

    public void start(@NonNull Instant startedAt) {
        validate();
        if (state != null) {
            throw new IllegalStateException("Transaction has already started");
        }
        effects = AccountEffects.derive(lines);
        receivedAt = startedAt;
        heldAt = null;
        failureReason = null;
        if (effects.isEmpty()) {
            this.hold(startedAt);
        } else {
            state = TransactionState.PLACING_HOLDS;
        }
    }

    public void placeEffect(
            @NonNull AccountNumber accountNumber,
            @NonNull Instant now) {
        AccountEffect effect = effect(accountNumber);
        if (effect.isAwaitingHold()
                && state != TransactionState.PLACING_HOLDS
                && state != TransactionState.RELEASING_HOLDS) {
            throw unexpectedTransactionState("place");
        }
        if (!effect.hold()) {
            return;
        }
        switch (state) {
            case PLACING_HOLDS, HELD -> {
                // Continue below to determine whether every effect is held.
            }
            case RELEASING_HOLDS -> {
                effect.beginRelease();
                return;
            }
            case SETTLING, COMPLETED -> {
                return;
            }
            case null, default -> throw unexpectedTransactionState("place");
        }
        if (effects.areAllHeld()) {
            this.hold(now);
        }
    }

    public void declineEffect(
            @NonNull AccountNumber accountNumber,
            String reason) {
        AccountEffect effect = effect(accountNumber);
        if (effect.isAwaitingHold()
                && state != TransactionState.PLACING_HOLDS
                && state != TransactionState.RELEASING_HOLDS) {
            throw unexpectedTransactionState("decline");
        }
        if (effect.decline()) {
            failureReason = reason;
        }
        beginRelease();
    }

    public void releaseEffect(@NonNull AccountNumber accountNumber) {
        AccountEffect effect = effect(accountNumber);
        effect.release();
        rejectIfFinished();
    }

    public void beginSettlement(String journalRequestId) {
        if (!requestId.equals(journalRequestId)) {
            throw new IllegalArgumentException("Journal entry does not belong to transaction");
        }
        switch (state) {
            case HELD -> {
                // Continue with settlement initialization.
            }
            case SETTLING, COMPLETED -> {
                return;
            }
            case null, default -> throw new IllegalStateException(
                    "Transaction account holds are not complete");
        }
        if (effects.isEmpty()) {
            complete();
        } else {
            beginSettlement();
        }
    }

    public void settleEffect(@NonNull AccountNumber accountNumber) {
        AccountEffect effect = effect(accountNumber);
        effect.settle();
        if (effects.areAllSettled()) {
            complete();
        }
    }

    public void requireJournalizable() {
        validate();
        if (state != TransactionState.HELD) {
            throw new IllegalStateException("Transaction account holds are not complete");
        }
    }

    private void hold(Instant now) {
        state = TransactionState.HELD;
        if (heldAt == null) {
            heldAt = now;
        }
    }

    private void beginRelease() {
        state = TransactionState.RELEASING_HOLDS;
        effects.beginReleaseOfHeldEffects();
        rejectIfFinished();
    }

    private void beginSettlement() {
        state = TransactionState.SETTLING;
        effects.beginSettlement();
    }

    private void complete() {
        state = TransactionState.COMPLETED;
    }

    private AccountEffect effect(AccountNumber accountNumber) {
        return effects.require(accountNumber);
    }

    private IllegalStateException unexpectedTransactionState(String operation) {
        return new IllegalStateException(
                "Cannot " + operation + " account hold while transaction is " + state);
    }

    private void rejectIfFinished() {
        if (effects.isReleaseFinished()) {
            state = TransactionState.REJECTED;
        }
    }

    private void validate() {
        requireText(requestId, "requestId");
        requireText(documentType, "documentType");
        requireText(documentNumber, "documentNumber");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (lines == null || lines.size() < 2) {
            throw new PostingRejectedException("A transaction needs at least two lines");
        }
        long debits = 0;
        long credits = 0;
        Currency currency = null;
        for (TransactionLine line : lines) {
            Objects.requireNonNull(line, "line");
            line.validate();
            if (currency != null && !currency.equals(line.getLedgerAmount().currency())) {
                throw new PostingRejectedException("All ledger amounts must use one currency");
            }
            currency = line.getLedgerAmount().currency();
            if (line.getSide() == DebitCredit.DEBIT) {
                debits = Math.addExact(debits, line.getLedgerAmount().minor());
            } else {
                credits = Math.addExact(credits, line.getLedgerAmount().minor());
            }
        }
        if (debits != credits) {
            throw new PostingRejectedException("Ledger debits and credits must balance");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PostingRejectedException(name + " must not be blank");
        }
    }
}
