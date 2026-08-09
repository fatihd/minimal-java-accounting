package dev.minimalaccounting.core.transaction;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.shared.PostingRejectedException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private List<AccountEffect> effects = new ArrayList<>();

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
            @NonNull List<AccountEffect> effects) {
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
        this.effects = new ArrayList<>(effects);
    }

    public List<TransactionLine> getLines() {
        return List.copyOf(lines);
    }

    public List<AccountEffect> getEffects() {
        return List.copyOf(effects);
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
        Map<Long, Long> changes = new LinkedHashMap<>();
        Map<Long, TransactionLine> accounts = new LinkedHashMap<>();
        for (TransactionLine line : lines) {
            TransactionLine existing = accounts.putIfAbsent(line.getAccountId(), line);
            if (existing != null
                    && !existing.getAccountNumber().equals(line.getAccountNumber())) {
                throw new PostingRejectedException(
                        "Account identity does not match transaction lines");
            }
            long signed = line.getSide().signed(line.getLedgerAmount().minor());
            changes.merge(line.getAccountId(), signed, Math::addExact);
        }
        effects = new ArrayList<>();
        for (Map.Entry<Long, Long> change : changes.entrySet()) {
            if (change.getValue() != 0) {
                effects.add(accountEffect(
                        change.getKey(), accounts.get(change.getKey()), change.getValue()));
            }
        }
        receivedAt = startedAt;
        heldAt = effects.isEmpty() ? startedAt : null;
        failureReason = null;
        state = effects.isEmpty() ? TransactionState.HELD : TransactionState.PLACING_HOLDS;
    }

    public void placeEffect(
            long accountId,
            int effectNumber,
            @NonNull Instant now) {
        AccountEffect effect = effect(accountId, effectNumber);
        switch (effect.getState()) {
            case PENDING -> {
                switch (state) {
                    case PLACING_HOLDS -> effect.hold();
                    case RELEASING_HOLDS -> {
                        effect.beginRelease();
                        return;
                    }
                    case null, default -> throw unexpectedEffectState("place", effect);
                }
            }
            case HELD -> {
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
                    case null, default -> throw unexpectedEffectState("place", effect);
                }
            }
            case RELEASING, RELEASED, SETTLING, SETTLED -> {
                return;
            }
            case DECLINED -> throw new IllegalStateException(
                    "Account hold cannot be both placed and declined");
        }
        boolean complete = effects.stream()
                .allMatch(candidate -> candidate.getState() == AccountEffectState.HELD);
        if (complete) {
            state = TransactionState.HELD;
            if (heldAt == null) {
                heldAt = now;
            }
        }
    }

    public void declineEffect(
            long accountId,
            int effectNumber,
            String reason) {
        AccountEffect effect = effect(accountId, effectNumber);
        switch (effect.getState()) {
            case PENDING -> {
                switch (state) {
                    case PLACING_HOLDS, RELEASING_HOLDS -> {
                        effect.decline();
                        failureReason = reason;
                    }
                    case null, default -> throw unexpectedEffectState("decline", effect);
                }
            }
            case DECLINED -> {
                // A duplicate refusal reconfirms the same release decision.
            }
            default -> throw new IllegalStateException(
                    "Account hold cannot be both declined and "
                            + effect.getState().name().toLowerCase());
        }
        state = TransactionState.RELEASING_HOLDS;
        for (AccountEffect candidate : effects) {
            if (candidate.getState() == AccountEffectState.HELD) {
                candidate.beginRelease();
            }
        }
        rejectIfFinished();
    }

    public void releaseEffect(long accountId, int effectNumber) {
        AccountEffect effect = effect(accountId, effectNumber);
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
            state = TransactionState.COMPLETED;
        } else {
            state = TransactionState.SETTLING;
            for (AccountEffect effect : effects) {
                effect.beginSettlement();
            }
        }
    }

    public void settleEffect(long accountId, int effectNumber) {
        AccountEffect effect = effect(accountId, effectNumber);
        effect.settle();
        boolean complete = effects.stream()
                .allMatch(candidate -> candidate.getState() == AccountEffectState.SETTLED);
        if (complete) {
            state = TransactionState.COMPLETED;
        }
    }

    public void requireJournalizable() {
        validate();
        if (state != TransactionState.HELD) {
            throw new IllegalStateException("Transaction account holds are not complete");
        }
    }

    private AccountEffect effect(long accountId, int effectNumber) {
        if (effectNumber < 0 || effectNumber >= effects.size()) {
            throw new IllegalArgumentException("Transaction effect does not exist");
        }
        AccountEffect effect = effects.get(effectNumber);
        if (effect.getAccountId() != accountId) {
            throw new IllegalArgumentException("Account does not match transaction effect");
        }
        return effect;
    }

    private static IllegalStateException unexpectedEffectState(
            String operation,
            AccountEffect effect) {
        return new IllegalStateException(
                "Cannot " + operation + " account hold in " + effect.getState() + " state");
    }

    private static AccountEffect accountEffect(
            long accountId,
            TransactionLine line,
            long signedAmount) {
        DebitCredit side = signedAmount > 0 ? DebitCredit.DEBIT : DebitCredit.CREDIT;
        return new AccountEffect(
                accountId,
                line.getAccountNumber(),
                side,
                new Money(
                        signedAmount > 0
                                ? signedAmount
                                : Math.negateExact(signedAmount),
                        line.getLedgerAmount().currency()),
                AccountEffectState.PENDING);
    }

    private void rejectIfFinished() {
        boolean finished = effects.stream()
                .allMatch(AccountEffect::releaseFinished);
        if (finished) {
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
