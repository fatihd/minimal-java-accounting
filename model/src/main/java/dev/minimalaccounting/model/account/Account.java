package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public class Account {
    @Setter
    private Long id;
    @Setter
    private Long revision;
    private AccountNumber accountNumber;
    private String name;
    private Money balance;
    @Getter(AccessLevel.NONE)
    private List<AccountHold> holds = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    private BalanceLimits balanceLimits = new BalanceLimits(null, null);

    public static Account create(
            AccountNumber accountNumber,
            String name,
            @NonNull Money balance,
            Money minimumBalance,
            Money maximumBalance) {
        Money openingBalance = balance;
        return new Account(
                null,
                null,
                accountNumber,
                name,
                openingBalance,
                minimumBalance,
                maximumBalance,
                List.of());
    }

    public Account(
            Long id,
            Long revision,
            @NonNull AccountNumber accountNumber,
            String name,
            @NonNull Money balance,
            Money minimumBalance,
            Money maximumBalance,
            @NonNull List<AccountHold> holds) {
        this.id = id;
        this.revision = revision;
        this.accountNumber = accountNumber;
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.balance = balance;
        this.balanceLimits = new BalanceLimits(minimumBalance, maximumBalance);
        this.holds = new ArrayList<>(holds);
        validateHolds();
        validateProjectedBalance(projectedBalance());
    }

    public AccountHoldState placeHold(
            long transactionId,
            int effectNumber,
            DebitCredit side,
            Money amount) {
        AccountHold existing = hold(transactionId, effectNumber);
        if (existing != null) {
            existing.requireMatches(side, amount);
            if (existing.getState() == AccountHoldState.DECLINED) {
                throw new PostingRejectedException(existing.getFailureReason());
            }
            return existing.getState();
        }
        AccountHold accountHold = new AccountHold(
                transactionId, effectNumber, side, amount, AccountHoldState.HELD, null);
        try {
            validateHold(side, amount);
            Money projected = side.applyTo(projectedBalance(), amount);
            validateProjectedBalance(projected);
        } catch (PostingRejectedException rejection) {
            accountHold.decline(rejection.getMessage());
            holds.add(accountHold);
            throw rejection;
        }
        holds.add(accountHold);
        return accountHold.getState();
    }

    public AccountHoldState releaseHold(
            long transactionId,
            int effectNumber,
            DebitCredit side,
            Money amount) {
        AccountHold accountHold = requireHold(transactionId, effectNumber);
        accountHold.requireMatches(side, amount);
        accountHold.release();
        return accountHold.getState();
    }

    public AccountHoldState settleHold(
            long transactionId,
            int effectNumber,
            DebitCredit side,
            Money amount) {
        AccountHold accountHold = requireHold(transactionId, effectNumber);
        accountHold.requireMatches(side, amount);
        if (accountHold.getState() == AccountHoldState.SETTLED) {
            return AccountHoldState.SETTLED;
        }
        Money nextBalance = side.applyTo(balance, amount);
        validateProjectedBalance(nextBalance);
        accountHold.settle();
        balance = nextBalance;
        return accountHold.getState();
    }

    public void requireCurrency(@NonNull Money amount) {
        if (!balance.hasSameCurrency(amount)) {
            throw new PostingRejectedException("Ledger currency for account "
                    + accountNumber.value() + " must be " + balance.currency());
        }
    }

    public Money projectedBalance(long signedChange) {
        Money next = balance.plusMinor(signedChange);
        validateProjectedBalance(next);
        return next;
    }

    public Money getMinimumBalance() {
        return balanceLimits.minimum();
    }

    public Money getMaximumBalance() {
        return balanceLimits.maximum();
    }

    public List<AccountHold> getHolds() {
        return List.copyOf(holds);
    }

    public Money getDebitsHeld() {
        return held(DebitCredit.DEBIT);
    }

    public Money getCreditsHeld() {
        return held(DebitCredit.CREDIT);
    }

    private void validateHold(
            @NonNull DebitCredit side,
            @NonNull Money amount) {
        requireCurrency(amount);
    }

    private void validateProjectedBalance(Money projected) {
        balanceLimits.requireContains(projected);
    }

    private void validateHolds() {
        Set<HoldIdentity> identities = new HashSet<>();
        for (AccountHold accountHold : holds) {
            accountHold.validate();
            requireCurrency(accountHold.getAmount(), balance, "hold amount");
            HoldIdentity identity = new HoldIdentity(
                    accountHold.getTransactionId(), accountHold.getEffectNumber());
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("Account hold identity must be unique");
            }
        }
    }

    private AccountHold requireHold(long transactionId, int effectNumber) {
        AccountHold accountHold = hold(transactionId, effectNumber);
        if (accountHold == null) {
            throw new IllegalStateException("Account hold does not exist");
        }
        return accountHold;
    }

    private AccountHold hold(long transactionId, int effectNumber) {
        for (AccountHold candidate : holds) {
            if (candidate.getTransactionId() == transactionId
                    && candidate.getEffectNumber() == effectNumber) {
                return candidate;
            }
        }
        return null;
    }

    private Money projectedBalance() {
        return balance.plus(getDebitsHeld()).minus(getCreditsHeld());
    }

    private Money held(DebitCredit side) {
        Money result = Money.zero(balance.currency());
        for (AccountHold accountHold : holds) {
            if (accountHold.getState() == AccountHoldState.HELD
                    && accountHold.getSide() == side) {
                result = result.plus(accountHold.getAmount());
            }
        }
        return result;
    }

    private static Money requireCurrency(Money amount, Money balance, String name) {
        if (!balance.hasSameCurrency(amount)) {
            throw new IllegalArgumentException(name + " must use " + balance.currency());
        }
        return amount;
    }

    private record HoldIdentity(long transactionId, int effectNumber) {
    }
}
