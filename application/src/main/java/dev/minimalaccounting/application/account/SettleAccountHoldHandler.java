package dev.minimalaccounting.application.account;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class SettleAccountHoldHandler implements Handler<SettleAccountHold> {
    @NonNull
    private final Repository<AccountNumber, Account> accounts;
    @NonNull
    private final Sender<AccountHoldSettled> settled;

    @Override
    public void handle(SettleAccountHold command) {
        var account = accounts.get(command.accountNumber()).orElseThrow();
        account.settleHold(
                command.transactionId(), command.effectNumber(),
                command.side(), command.amount());
        accounts.update(account);
        settled.send(new AccountHoldSettled(
                command.accountNumber(), command.transactionId(), command.effectNumber()));
    }
}
