package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public final class AccountHold {
    private long transactionId;
    private int effectNumber;
    private DebitCredit side;
    private Money amount;
    @Setter(AccessLevel.NONE)
    private AccountHoldState state;
    @Setter(AccessLevel.NONE)
    private String failureReason;

    public AccountHold(
            long transactionId,
            int effectNumber,
            DebitCredit side,
            Money amount,
            AccountHoldState state,
            String failureReason) {
        this.transactionId = transactionId;
        this.effectNumber = effectNumber;
        this.side = side;
        this.amount = amount;
        this.state = state;
        this.failureReason = failureReason;
        validate();
    }

    static AccountHold placed(
            long transactionId,
            int effectNumber,
            DebitCredit side,
            Money amount) {
        return new AccountHold(
                transactionId,
                effectNumber,
                side,
                amount,
                AccountHoldState.HELD,
                null);
    }

    AccountHoldState confirmPlacement(DebitCredit expectedSide, Money expectedAmount) {
        requireMatches(expectedSide, expectedAmount);
        if (state == AccountHoldState.DECLINED) {
            throw new PostingRejectedException(failureReason);
        }
        return state;
    }

    Identifier identifier() {
        return new Identifier(transactionId, effectNumber);
    }

    boolean hasIdentity(long expectedTransactionId, int expectedEffectNumber) {
        return transactionId == expectedTransactionId && effectNumber == expectedEffectNumber;
    }

    void validate() {
        if (transactionId < 1) {
            throw new IllegalArgumentException("transactionId must be positive");
        }
        if (effectNumber < 0) {
            throw new IllegalArgumentException("effectNumber must not be negative");
        }
        Objects.requireNonNull(side, "side");
        Money value = Objects.requireNonNull(amount, "amount");
        if (value.minor() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(state, "state");
        if (state == AccountHoldState.DECLINED
                && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("Declined account hold needs a reason");
        }
        if (state != AccountHoldState.DECLINED && failureReason != null) {
            throw new IllegalArgumentException("Only a declined account hold has a reason");
        }
    }

    void requireMatches(DebitCredit expectedSide, Money expectedAmount) {
        if (side != expectedSide || !amount.equals(expectedAmount)) {
            throw new IllegalArgumentException(
                    "Account hold identity has conflicting business data");
        }
    }

    void release() {
        state = switch (state) {
            case HELD, RELEASED -> AccountHoldState.RELEASED;
            case null, default -> throw new IllegalStateException(
                    "Account hold in " + state + " state cannot be released");
        };
    }

    void decline(String reason) {
        if (state != AccountHoldState.HELD) {
            throw new IllegalStateException("Only a new account hold can be declined");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Declined account hold needs a reason");
        }
        state = AccountHoldState.DECLINED;
        failureReason = reason;
    }

    void settle() {
        state = switch (state) {
            case HELD, SETTLED -> AccountHoldState.SETTLED;
            case null, default -> throw new IllegalStateException(
                    "Account hold in " + state + " state cannot be settled");
        };
    }

    boolean requiresSettlement() {
        return switch (state) {
            case HELD -> true;
            case SETTLED -> false;
            case null, default -> throw new IllegalStateException(
                    "Account hold in " + state + " state cannot be settled");
        };
    }

    Money heldAmount(DebitCredit expectedSide) {
        if (state == AccountHoldState.HELD && side == expectedSide) {
            return amount;
        }
        return Money.zero(amount.currency());
    }

    record Identifier(long transactionId, int effectNumber) {
    }
}
